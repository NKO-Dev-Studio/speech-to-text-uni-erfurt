package studio.nkodev.stt.storage.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Testsuite of {@link SharedAudioFileStorage}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
class SharedAudioFileStorageTest {

  @TempDir private Path tempDir;

  @Test
  void shouldResolveExistingSharedAudioFile() throws Exception {
    Path sharedStoragePath = tempDir.resolve("shared-audio");
    Files.createDirectories(sharedStoragePath);
    Path storedAudioPath = copyTestAudioTo(sharedStoragePath.resolve("test-audio"));
    byte[] expectedContent = readTestAudio();

    SharedAudioFileStorage sharedAudioFileStorage = new SharedAudioFileStorage(sharedStoragePath);

    Path resolvedAudioPath = sharedAudioFileStorage.getAudioFilePath("test-audio");

    assertEquals(storedAudioPath.toAbsolutePath().normalize(), resolvedAudioPath);
    assertArrayEquals(expectedContent, Files.readAllBytes(resolvedAudioPath));
  }

  @Test
  void shouldRejectNonExistingSharedAudioStorageDirectory() {
    Path sharedStoragePath = tempDir.resolve("shared-audio");

    assertThrows(
        IllegalArgumentException.class, () -> new SharedAudioFileStorage(sharedStoragePath));
  }

  @Test
  void shouldRejectBlankFileName() throws Exception {
    Path sharedStoragePath = tempDir.resolve("shared-audio");
    Files.createDirectories(sharedStoragePath);
    SharedAudioFileStorage sharedAudioFileStorage = new SharedAudioFileStorage(sharedStoragePath);

    assertThrows(IllegalArgumentException.class, () -> sharedAudioFileStorage.getAudioFilePath(" "));
  }

  @Test
  void shouldThrowStorageExceptionWhenSharedAudioFileDoesNotExist() throws Exception {
    Path sharedStoragePath = tempDir.resolve("shared-audio");
    Files.createDirectories(sharedStoragePath);
    SharedAudioFileStorage sharedAudioFileStorage = new SharedAudioFileStorage(sharedStoragePath);

    assertThrows(StorageException.class, () -> sharedAudioFileStorage.getAudioFilePath("test-audio"));
  }

  @Test
  void shouldRejectFileResolutionOutsideSharedStorage() throws Exception {
    Path sharedStoragePath = tempDir.resolve("shared-audio");
    Files.createDirectories(sharedStoragePath);
    SharedAudioFileStorage sharedAudioFileStorage = new SharedAudioFileStorage(sharedStoragePath);

    assertThrows(StorageException.class, () -> sharedAudioFileStorage.getAudioFilePath("../test-audio"));
  }

  private byte[] readTestAudio() throws IOException {
    try (InputStream inputStream = SharedAudioFileStorageTest.class.getResourceAsStream("test-audio")) {
      assertNotNull(inputStream);
      return inputStream.readAllBytes();
    }
  }

  private Path copyTestAudioTo(Path targetPath) throws IOException {
    try (InputStream inputStream = SharedAudioFileStorageTest.class.getResourceAsStream("test-audio")) {
      assertNotNull(inputStream);
      Files.copy(inputStream, targetPath);
      return targetPath;
    }
  }
}
