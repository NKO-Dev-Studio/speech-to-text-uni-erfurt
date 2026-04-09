package studio.nkodev.stt.client.config;

import java.nio.file.Path;

/**
 * Factory creating supported transfer configurations.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public final class SpeechToTextTransferConfigurationFactory {

  private SpeechToTextTransferConfigurationFactory() {}

  /**
   * Creates a transfer configuration that exchanges data via gRPC streaming.
   *
   * <p>Use this variant when audio files or result files should be transferred over the network
   * connection to the speech-to-text-service.
   *
   * @return streaming-based transfer configuration
   */
  public static SpeechToTextTransferConfiguration streaming() {
    return new SpeechToTextTransferConfiguration(SpeechToTextTransferType.STREAMING, null);
  }

  /**
   * Creates a transfer configuration that exchanges data through a shared filesystem directory.
   *
   * <p>Use this variant when client and service can access the same directory and files should not
   * be transferred over the gRPC connection.
   *
   * @param sharedStorageDirectory shared directory used to exchange files
   * @return shared-storage-based transfer configuration
   */
  public static SpeechToTextTransferConfiguration sharedStorage(Path sharedStorageDirectory) {
    return new SpeechToTextTransferConfiguration(
        SpeechToTextTransferType.SHARED_STORAGE, sharedStorageDirectory);
  }
}
