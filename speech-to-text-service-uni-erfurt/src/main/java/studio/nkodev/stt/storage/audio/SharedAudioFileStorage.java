package studio.nkodev.stt.storage.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * When running the speech-to-text-service on the same host as the speech-to-text-task triggering
 * component, the audio file can be provided via a file inside a specified shared directory. <br>
 * With this approach avoid the need to transfer the files over the network stack
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.03.26
 */
public class SharedAudioFileStorage {

  private static final Logger logger = LoggerFactory.getLogger(SharedAudioFileStorage.class);

  private final Path sharedAudioStoragePath;

  public SharedAudioFileStorage(Path sharedAudioStoragePath) {
    if (sharedAudioStoragePath == null || !Files.isDirectory(sharedAudioStoragePath)) {
      throw new IllegalArgumentException(
          "No shared audio storage path provided or the specified path is not an existing directory");
    }

    this.sharedAudioStoragePath = sharedAudioStoragePath.toAbsolutePath().normalize();
  }

  public Path getAudioFilePath(String fileName) throws StorageException {
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("No file name provided");
    }

    Path targetFilePath = sharedAudioStoragePath.resolve(fileName).normalize();
    if (!targetFilePath.startsWith(sharedAudioStoragePath)) {
      throw new StorageException("Invalid target file inside shared audio storage");
    }

    logger.trace("Resolving shared audio file {}", targetFilePath);
    if (!Files.exists(targetFilePath) || !Files.isRegularFile(targetFilePath)) {
      throw new StorageException("Target file not found inside shared storage");
    }

    return targetFilePath;
  }
}
