package studio.nkodev.stt.storage.result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;

/**
 * Consumer which can be used to read the results of a speech to text task. <br>
 * On close the result files are being removed from filesystem.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 15.03.26
 */
public class SpeechToTextTaskResultConsumer implements AutoCloseable {

  private static final Logger logger =
      LoggerFactory.getLogger(SpeechToTextTaskResultConsumer.class);

  private final SpeechToTextTaskResultStorage resultStorage;
  private final long taskId;

  SpeechToTextTaskResultConsumer(long taskId, SpeechToTextTaskResultStorage resultStorage) {
    this.resultStorage = resultStorage;
    this.taskId = taskId;
  }

  /** @return the path of the persisted result file */
  public Path getResultFilePath() throws IOException, NotFoundStorageException {
    Path taskResultDirectory = resultStorage.getTaskResultDirectoryPath(taskId);
    try (Stream<Path> resultFiles = Files.list(taskResultDirectory)) {
      List<Path> collectedResultFiles = resultFiles.filter(Files::isRegularFile).toList();
      if (collectedResultFiles.isEmpty()) {
        throw new IllegalStateException("No result file available for task " + taskId);
      }
      if (collectedResultFiles.size() > 1) {
        logger.warn("Detected multiple result files for task {}: {}", taskId, collectedResultFiles);
        throw new IllegalStateException("Expected exactly one result file for task " + taskId);
      }
      return collectedResultFiles.getFirst();
    }
  }

  public long getTaskId() {
    return taskId;
  }

  @Override
  public void close() {
    try {
      resultStorage.removeResultsOfTask(taskId);
    } catch (Exception exception) {
      logger.error("Error during removing results of task {}.", taskId, exception);
    }
  }
}
