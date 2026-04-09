package studio.nkodev.stt.storage.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.storage.exception.AlreadyExistsStorageException;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * This storage is used to persist the related audio files of a task
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 16.03.26
 */
public class AudioFileStorage {

  private static final Logger logger = LoggerFactory.getLogger(AudioFileStorage.class);
  private static final String AUDIO_FILE_SUFFIX = ".audio";
  private static final String AUDIO_FILE_UPLOAD_SUFFIX = ".audio.upload";

  private final Path audioFileStoragePath;

  public AudioFileStorage(Path audioFileStoragePath) throws StorageException {
    this.audioFileStoragePath = audioFileStoragePath;
    initAudioFileStorageLocation(audioFileStoragePath);
  }

  /**
   * The service can be configured to use the local filesystem to provide audio files. This can
   * improve the performance when client and service are executed on the same host, because no
   * network transfer is necessary. <br>
   * This method moves the file from the shared storage into the audio file storage of this service.
   *
   * @param taskId to which the file related to
   * @param sourcePath of the audio file moved into this storage
   * @throws StorageException when an error occurred during accessing the file inside the shared
   *     storage or persisting it into its final destination of the {@link AudioFileStorage}
   */
  void moveFileIntoStorage(long taskId, Path sourcePath) throws StorageException {
    Path target = createAudioFilePath(taskId);
    verifyTargetDoesNotExist(taskId, target);

    try {
      Files.move(sourcePath, target);
    } catch (IOException e) {
      throw new StorageException(
          "Failed to move audio file of task " + taskId + " into storage", e);
    }
  }

  public void provideAudioFile(AudioFileProvider audioFileProvider) throws StorageException {
    audioFileProvider.persistIntoStorage(this);
  }

  public AudioFileChunkConsumer createAudioFileChunkConsumer(long taskId) throws StorageException {
    Path target = createAudioFilePath(taskId);
    verifyTargetDoesNotExist(taskId, target);

    Path temporaryTarget = createTemporaryAudioFilePath(taskId);
    if (Files.exists(temporaryTarget)) {
      throw new AlreadyExistsStorageException(taskId);
    }

    try {
      OutputStream outputStream =
          Files.newOutputStream(temporaryTarget, StandardOpenOption.CREATE_NEW);
      return new StorageAudioFileChunkConsumer(taskId, target, temporaryTarget, outputStream);
    } catch (IOException e) {
      throw new StorageException(
          "Failed to initialize audio chunk consumer for task " + taskId, e);
    }
  }

  public Path getAudioFilePathOfTask(long taskId) throws NotFoundStorageException {
    Path audioFilePath = createAudioFilePath(taskId);
    if (Files.exists(audioFilePath) && Files.isRegularFile(audioFilePath)) {
      return audioFilePath;
    }

    throw new NotFoundStorageException("speech-to-text-task-audio-file", taskId);
  }

  public void deleteAudioFileFromStorage(long taskId)
      throws StorageException {
    Path audioFilePath = createAudioFilePath(taskId);

    try {
      if(Files.exists(audioFilePath)) {
        Files.delete(audioFilePath);
      }
    } catch (IOException e) {
      throw new StorageException("Failed to delete audio file of task " + taskId, e);
    }
  }

  private static void initAudioFileStorageLocation(Path audioFileStoragePath)
      throws StorageException {
    if (Files.notExists(audioFileStoragePath)) {
      logger.info("Init audio file storage location");

      try {
        Files.createDirectories(audioFileStoragePath);
      } catch (IOException e) {
        throw new StorageException(
            "Failed to initialize audio file storage location " + audioFileStoragePath, e);
      }
    }
  }

  private Path createAudioFilePath(long taskId) {
    return audioFileStoragePath.resolve(taskId + AUDIO_FILE_SUFFIX);
  }

  private Path createTemporaryAudioFilePath(long taskId) {
    return audioFileStoragePath.resolve(taskId + AUDIO_FILE_UPLOAD_SUFFIX);
  }

  private static void verifyTargetDoesNotExist(long taskId, Path target)
      throws AlreadyExistsStorageException {
    if (Files.exists(target)) {
      throw new AlreadyExistsStorageException(taskId);
    }
  }

  private static final class StorageAudioFileChunkConsumer implements AudioFileChunkConsumer {

    private final long taskId;
    private final Path target;
    private final Path temporaryTarget;
    private final OutputStream outputStream;
    private boolean completed;
    private boolean closed;

    private StorageAudioFileChunkConsumer(
        long taskId, Path target, Path temporaryTarget, OutputStream outputStream) {
      this.taskId = taskId;
      this.target = target;
      this.temporaryTarget = temporaryTarget;
      this.outputStream = outputStream;
    }

    @Override
    public void consume(AudioFileChunk audioFileChunk) throws StorageException {
      if (closed) {
        throw new IllegalStateException("Audio chunk consumer already closed");
      }

      try {
        outputStream.write(audioFileChunk.content());
        if (audioFileChunk.lastChunk()) {
          completeUpload();
        }
      } catch (IOException e) {
        throw new StorageException("Failed to persist audio chunk of task " + taskId, e);
      }
    }

    @Override
    public void close() throws StorageException {
      if (closed) {
        return;
      }

      closed = true;
      try {
        outputStream.close();
      } catch (IOException e) {
        throw new StorageException("Failed to close audio chunk consumer of task " + taskId, e);
      } finally {
        if (!completed) {
          try {
            Files.deleteIfExists(temporaryTarget);
          } catch (IOException e) {
            logger.warn(
                "Failed to remove incomplete audio upload {} of task {}",
                temporaryTarget,
                taskId,
                e);
          }
        }
      }
    }

    private void completeUpload() throws StorageException {
      try {
        outputStream.close();
        Files.move(temporaryTarget, target, StandardCopyOption.ATOMIC_MOVE);
        completed = true;
        closed = true;
      } catch (IOException e) {
        throw new StorageException("Failed to finalize audio upload of task " + taskId, e);
      }
    }
  }
}
