package studio.nkodev.stt.storage.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpeechToTextTaskResultConsumerTest {

  @TempDir private Path tempDir;

  @Test
  void shouldReturnSingleResultFilePath() throws Exception {
    SpeechToTextTaskResultStorage resultStorage = createResultStorage();
    Path resultDirectory = resultStorage.createTaskResultDirectory(1L);
    Path resultFile = Files.createFile(resultDirectory.resolve("result.txt"));

    try (SpeechToTextTaskResultConsumer resultConsumer = resultStorage.createTaskResultConsumer(1L)) {
      assertEquals(resultFile, resultConsumer.getResultFilePath());
    }
  }

  @Test
  void shouldRejectTaskWithoutResultFile() throws Exception {
    SpeechToTextTaskResultStorage resultStorage = createResultStorage();
    resultStorage.createTaskResultDirectory(1L);

    try (SpeechToTextTaskResultConsumer resultConsumer = resultStorage.createTaskResultConsumer(1L)) {
      assertThrows(IllegalStateException.class, resultConsumer::getResultFilePath);
    }
  }

  @Test
  void shouldRejectTaskWithMultipleResultFiles() throws Exception {
    SpeechToTextTaskResultStorage resultStorage = createResultStorage();
    Path resultDirectory = resultStorage.createTaskResultDirectory(1L);
    Files.createFile(resultDirectory.resolve("result.txt"));
    Files.createFile(resultDirectory.resolve("result.json"));

    try (SpeechToTextTaskResultConsumer resultConsumer = resultStorage.createTaskResultConsumer(1L)) {
      assertThrows(IllegalStateException.class, resultConsumer::getResultFilePath);
    }
  }

  private SpeechToTextTaskResultStorage createResultStorage() {
    return new SpeechToTextTaskResultStorage(
        new SpeechToTextTaskResultDirectoryManager(tempDir.resolve("results")));
  }
}
