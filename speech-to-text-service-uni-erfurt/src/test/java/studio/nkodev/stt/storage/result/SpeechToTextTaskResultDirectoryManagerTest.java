package studio.nkodev.stt.storage.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testsuite of {@link SpeechToTextTaskResultDirectoryManager}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
class SpeechToTextTaskResultDirectoryManagerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldReturnEmptyOptionalWhenTaskResultDirectoryDoesNotExist() {
    SpeechToTextTaskResultDirectoryManager directoryManager = createDirectoryManager();

    Optional<Path> resultDirectory = directoryManager.getTaskResultDirectory(7L);

    assertTrue(resultDirectory.isEmpty());
  }

  @Test
  void shouldReturnTaskResultDirectoryWhenItExists() throws Exception {
    SpeechToTextTaskResultDirectoryManager directoryManager = createDirectoryManager();
    Path expectedTaskDirectory = expectedTaskDirectory(7L);
    Files.createDirectories(expectedTaskDirectory);

    Optional<Path> resultDirectory = directoryManager.getTaskResultDirectory(7L);

    assertTrue(resultDirectory.isPresent());
    assertEquals(expectedTaskDirectory, resultDirectory.orElseThrow());
  }

  @Test
  void shouldCreateTaskResultDirectoryAtExpectedLocation() throws Exception {
    SpeechToTextTaskResultDirectoryManager directoryManager = createDirectoryManager();

    Path createdDirectory = directoryManager.createTaskResultDirectory(11L);

    assertEquals(expectedTaskDirectory(11L), createdDirectory);
    assertTrue(Files.isDirectory(createdDirectory));
  }

  @Test
  void shouldDeleteTaskResultDirectoryWithFilesAndDirectoriesInside() throws Exception {
    SpeechToTextTaskResultDirectoryManager directoryManager = createDirectoryManager();
    Path taskDirectory = directoryManager.createTaskResultDirectory(13L);
    Files.writeString(taskDirectory.resolve("result.txt"), "transcript");
    Path nestedDirectory = Files.createDirectories(taskDirectory.resolve("nested/child"));
    Files.writeString(nestedDirectory.resolve("metadata.json"), "{\"status\":\"done\"}");

    directoryManager.deleteTaskResultDirectory(13L);

    assertFalse(Files.exists(taskDirectory));
  }

  private SpeechToTextTaskResultDirectoryManager createDirectoryManager() {
    return new SpeechToTextTaskResultDirectoryManager(tempDir.resolve("results"));
  }

  private Path expectedTaskDirectory(long taskId) {
    return tempDir.resolve("results").resolve("stt-task-" + taskId);
  }
}
