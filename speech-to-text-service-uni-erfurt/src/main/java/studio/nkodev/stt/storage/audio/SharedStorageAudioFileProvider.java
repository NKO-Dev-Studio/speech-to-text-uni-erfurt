package studio.nkodev.stt.storage.audio;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Provider using a file from shared storage as audio file source
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 16.03.26
 */
class SharedStorageAudioFileProvider extends AudioFileProvider {

  private static final Logger logger =
      LoggerFactory.getLogger(SharedStorageAudioFileProvider.class);
  private final String fileName;
  private final SharedAudioFileStorage storage;

  public SharedStorageAudioFileProvider(
      SharedAudioFileStorage sharedAudioFileStorage, long taskId, String fileName) {
    super(taskId);
    this.fileName = fileName;
    this.storage = sharedAudioFileStorage;
  }

  @Override
  public void persistIntoStorage(AudioFileStorage audioFileStorage) throws StorageException {
    logger.debug("Start persisting audio file from shared storage");
    Path sourceAudioFile = storage.getAudioFilePath(fileName);
    audioFileStorage.moveFileIntoStorage(taskId, sourceAudioFile);
  }
}
