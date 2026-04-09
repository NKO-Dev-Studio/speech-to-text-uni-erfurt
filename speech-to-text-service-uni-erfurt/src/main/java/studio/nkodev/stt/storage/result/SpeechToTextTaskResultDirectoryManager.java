package studio.nkodev.stt.storage.result;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible to handle the creation and deletion of the speech to text task
 * directories in which the result files will be persisted in. <br>
 * For each task a subfolder will be created. A shutdown hook is registered ensuring the deletion of
 * the results after service shutdown.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public class SpeechToTextTaskResultDirectoryManager {

  private static final Logger logger =
      LoggerFactory.getLogger(SpeechToTextTaskResultDirectoryManager.class);
  private static final String TASK_RESULT_DIRECTORY_PREFIX = "stt-task-";

  private final Path resultTemporaryDirectory;

  public SpeechToTextTaskResultDirectoryManager(Path resultTemporaryDirectory) {
    this.resultTemporaryDirectory = resultTemporaryDirectory;
  }

  Optional<Path> getTaskResultDirectory(long taskId) {
    logger.debug("Check if task result directory for task {} exists", taskId);
    Path taskResultDirectory = createTaskResultDirectoryPath(taskId);

    if (Files.exists(taskResultDirectory) && Files.isDirectory(taskResultDirectory)) {
      return Optional.of(taskResultDirectory);
    }

    return Optional.empty();
  }

  Path createTaskResultDirectory(long taskId) throws IOException {
    logger.debug("Create task result directory for task {}", taskId);
    Path taskResultDirectory = createTaskResultDirectoryPath(taskId);
    Files.createDirectories(taskResultDirectory);

    return taskResultDirectory;
  }

  void deleteTaskResultDirectory(long taskId) throws IOException {
    logger.debug("Delete task result directory for task {}", taskId);
    Path taskResultDirectory = createTaskResultDirectoryPath(taskId);

    if (Files.exists(taskResultDirectory) && Files.isDirectory(taskResultDirectory)) {
      Files.walkFileTree(
          taskResultDirectory,
          new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              if (exc != null) {
                throw exc;
              }

              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  private Path createTaskResultDirectoryPath(long taskId) {
    return resultTemporaryDirectory.resolve(TASK_RESULT_DIRECTORY_PREFIX + taskId);
  }
}
