package studio.nkodev.stt.client.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration describing how data is transferred between client and service.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public record SpeechToTextTransferConfiguration(
    SpeechToTextTransferType transferType, Path sharedStorageDirectory) {

  public SpeechToTextTransferConfiguration {
    if (transferType == null) {
      throw new IllegalArgumentException("No transfer type provided");
    }
    if (transferType == SpeechToTextTransferType.STREAMING && sharedStorageDirectory != null) {
      throw new IllegalArgumentException(
          "A streaming transfer configuration must not define a shared storage directory");
    }
    if (transferType == SpeechToTextTransferType.SHARED_STORAGE
        && (sharedStorageDirectory == null || !Files.isDirectory(sharedStorageDirectory))) {
      throw new IllegalArgumentException(
          "A shared storage transfer configuration requires an existing directory");
    }
  }
}
