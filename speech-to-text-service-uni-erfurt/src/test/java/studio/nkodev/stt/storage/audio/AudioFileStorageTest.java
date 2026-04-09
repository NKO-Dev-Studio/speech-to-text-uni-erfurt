package studio.nkodev.stt.storage.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.nkodev.stt.storage.exception.AlreadyExistsStorageException;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;

/**
 * Testsuite of {@link AudioFileStorage}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
class AudioFileStorageTest {

  @TempDir private Path tempDir;

  @Test
  void shouldInitializeAudioFileStorageDirectory() throws Exception {
    Path storagePath = tempDir.resolve("audio/storage");

    new AudioFileStorage(storagePath);

    assertTrue(Files.isDirectory(storagePath));
  }

  @Test
  void shouldPersistProvidedAudioFileIntoStorage() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    byte[] expectedContent = readTestAudio();
    Path sourcePath = copyTestAudioTo(tempDir.resolve("test-audio-source"));

    audioFileStorage.provideAudioFile(new MoveFileAudioFileProvider(7L, sourcePath));

    Path persistedAudioFilePath = audioFileStorage.getAudioFilePathOfTask(7L);
    assertTrue(Files.exists(persistedAudioFilePath));
    assertFalse(Files.exists(sourcePath));
    assertArrayEquals(expectedContent, Files.readAllBytes(persistedAudioFilePath));
  }

  @Test
  void shouldRejectProvidedAudioFileWhenTaskAudioAlreadyExists() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    Path firstSourcePath = copyTestAudioTo(tempDir.resolve("test-audio-first"));
    Path secondSourcePath = copyTestAudioTo(tempDir.resolve("test-audio-second"));
    audioFileStorage.provideAudioFile(new MoveFileAudioFileProvider(7L, firstSourcePath));

    assertThrows(
        AlreadyExistsStorageException.class,
        () -> audioFileStorage.provideAudioFile(new MoveFileAudioFileProvider(7L, secondSourcePath)));
    assertTrue(Files.exists(secondSourcePath));
  }

  @Test
  void shouldPersistChunkedAudioUpload() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    byte[] audioContent = readTestAudio();
    int splitIndex = audioContent.length / 2;

    try (AudioFileChunkConsumer chunkConsumer = audioFileStorage.createAudioFileChunkConsumer(11L)) {
      chunkConsumer.consume(new AudioFileChunk(slice(audioContent, 0, splitIndex), false));
      chunkConsumer.consume(
          new AudioFileChunk(slice(audioContent, splitIndex, audioContent.length), true));
    }

    Path persistedAudioFilePath = audioFileStorage.getAudioFilePathOfTask(11L);
    assertEquals(tempDir.resolve("audio").resolve("11.audio"), persistedAudioFilePath);
    assertArrayEquals(audioContent, Files.readAllBytes(persistedAudioFilePath));
    assertFalse(Files.exists(tempDir.resolve("audio").resolve("11.audio.upload")));
  }

  @Test
  void shouldDeleteIncompleteChunkUploadOnClose() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    byte[] audioContent = readTestAudio();

    try (AudioFileChunkConsumer chunkConsumer = audioFileStorage.createAudioFileChunkConsumer(13L)) {
      chunkConsumer.consume(new AudioFileChunk(slice(audioContent, 0, audioContent.length / 2), false));
    }

    assertThrows(NotFoundStorageException.class, () -> audioFileStorage.getAudioFilePathOfTask(13L));
    assertFalse(Files.exists(tempDir.resolve("audio").resolve("13.audio.upload")));
  }

  @Test
  void shouldRejectChunkUploadWhenTaskAudioAlreadyExists() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    Path sourcePath = copyTestAudioTo(tempDir.resolve("test-audio-source"));
    audioFileStorage.provideAudioFile(new MoveFileAudioFileProvider(17L, sourcePath));

    assertThrows(
        AlreadyExistsStorageException.class,
        () -> audioFileStorage.createAudioFileChunkConsumer(17L));
  }

  @Test
  void shouldThrowNotFoundForMissingTaskAudio() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();

    assertThrows(NotFoundStorageException.class, () -> audioFileStorage.getAudioFilePathOfTask(19L));
  }

  @Test
  void shouldDeleteStoredAudioFile() throws Exception {
    AudioFileStorage audioFileStorage = createAudioFileStorage();
    Path sourcePath = copyTestAudioTo(tempDir.resolve("test-audio-source"));
    audioFileStorage.provideAudioFile(new MoveFileAudioFileProvider(23L, sourcePath));

    audioFileStorage.deleteAudioFileFromStorage(23L);

    assertFalse(Files.exists(tempDir.resolve("audio").resolve("23.audio")));
    assertThrows(NotFoundStorageException.class, () -> audioFileStorage.getAudioFilePathOfTask(23L));
  }

  private AudioFileStorage createAudioFileStorage() throws Exception {
    return new AudioFileStorage(tempDir.resolve("audio"));
  }

  private byte[] readTestAudio() throws IOException {
    try (InputStream inputStream = AudioFileStorageTest.class.getResourceAsStream("test-audio")) {
      assertNotNull(inputStream);
      return inputStream.readAllBytes();
    }
  }

  private Path copyTestAudioTo(Path targetPath) throws IOException {
    try (InputStream inputStream = AudioFileStorageTest.class.getResourceAsStream("test-audio")) {
      assertNotNull(inputStream);
      Files.copy(inputStream, targetPath);
      return targetPath;
    }
  }

  private byte[] slice(byte[] source, int startIndex, int endIndex) {
    byte[] slice = new byte[endIndex - startIndex];
    System.arraycopy(source, startIndex, slice, 0, slice.length);
    return slice;
  }

  private static final class MoveFileAudioFileProvider extends AudioFileProvider {

    private final Path sourcePath;

    private MoveFileAudioFileProvider(long taskId, Path sourcePath) {
      super(taskId);
      this.sourcePath = sourcePath;
    }

    @Override
    public void persistIntoStorage(AudioFileStorage audioFileStorage)
        throws studio.nkodev.stt.storage.exception.StorageException {
      audioFileStorage.moveFileIntoStorage(taskId, sourcePath);
    }
  }
}
