package studio.nkodev.stt.storage.result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.storage.exception.AlreadyExistsStorageException;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * This storage is responsible to hold the generated results of a {@link
 * SpeechToTextTask}.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 08.03.26
 */
public class SpeechToTextTaskResultStorage {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTaskResultStorage.class);

  private final SpeechToTextTaskResultDirectoryManager speechToTextTaskResultDirectoryManager;

  public SpeechToTextTaskResultStorage(
      SpeechToTextTaskResultDirectoryManager speechToTextTaskResultDirectoryManager) {
    this.speechToTextTaskResultDirectoryManager = speechToTextTaskResultDirectoryManager;
  }

  public Path createTaskResultDirectory(long taskId) throws StorageException {
    try {
      return speechToTextTaskResultDirectoryManager.createTaskResultDirectory(taskId);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public SpeechToTextTaskResultConsumer createTaskResultConsumer(long taskId) {
    return new SpeechToTextTaskResultConsumer(taskId, this);
  }

  /**
   * @param taskId of the task of which the results will be read
   * @return the {@link Path} of the directory in which the results are persisted in
   * @throws NotFoundStorageException
   */
  public Path getTaskResultDirectoryPath(long taskId) throws NotFoundStorageException {
    Optional<Path> taskResultDirectoryPath =
        speechToTextTaskResultDirectoryManager.getTaskResultDirectory(taskId);
    if (taskResultDirectoryPath.isPresent()) {
      return taskResultDirectoryPath.get();
    }

    throw new NotFoundStorageException("speech-to-text-task-result", taskId);
  }

  /**
   * Removes the results of the specified task from result storage
   *
   * @param taskId of which the results will be removed
   */
  public void removeResultsOfTask(long taskId) throws StorageException {
    logger.debug("Removing results of task {}", taskId);

    try {
      speechToTextTaskResultDirectoryManager.deleteTaskResultDirectory(taskId);
    } catch (IOException e) {
      throw new StorageException("Error during deleting results of task", e);
    }
  }
}
