package studio.nkodev.stt.storage.result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * In some setups the speech-to-text-service is hosted on the same machine as the service requesting
 * the execution of speech to text tasks. <br>
 * To avoid unnecessary network traffic, the result files can be written inside a shared storage.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.03.26
 */
public class SpeechToTextTaskSharedResultStorage {

  private static final Logger logger =
      LoggerFactory.getLogger(SpeechToTextTaskSharedResultStorage.class);

  private final Path sharedResultStoragePath;

  public SpeechToTextTaskSharedResultStorage(Path sharedResultStoragePath) {
    if (sharedResultStoragePath == null || !Files.isDirectory(sharedResultStoragePath)) {
      throw new IllegalArgumentException(
          "No shared result storage path provided or the specified path is not an existing directory");
    }
    this.sharedResultStoragePath = sharedResultStoragePath;
  }

  /**
   * Moves the files provided by the {@link SpeechToTextTaskResultConsumer} into the shared storage
   *
   * @return the path inside the shared storage
   */
  public Path persistResultIntoSharedStorage(SpeechToTextTaskResultConsumer resultConsumer)
      throws StorageException {
    logger.debug(
        "Moving results of task {} into shared result storage", resultConsumer.getTaskId());

    try {
      return moveFileIntoSharedStorage(resultConsumer.getResultFilePath());
    } catch (Exception e) {
      throw new StorageException("Error during putting results into shared result storage", e);
    }
  }

  private Path moveFileIntoSharedStorage(Path resultFile) throws IOException {
    String fileName = resultFile.getFileName().toString();
    int suffixStartIndex = fileName.lastIndexOf('.');
    String suffix = suffixStartIndex >= 0 ? fileName.substring(suffixStartIndex) : "";
    Path sharedStoragePath = sharedResultStoragePath.resolve(UUID.randomUUID() + suffix);

    logger.trace(
        "Moving result file {} into shared result storage location {}",
        resultFile,
        sharedStoragePath);
    Files.move(resultFile, sharedStoragePath, StandardCopyOption.REPLACE_EXISTING);

    return sharedStoragePath;
  }
}
