package studio.nkodev.stt.client.config;

import studio.nkodev.stt.client.SpeechToTextServiceClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration of the {@link SpeechToTextServiceClient}.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public record SpeechToTextServiceClientConfiguration(
    String host,
    int port,
    Path trustedServerCertificatePath,
    SpeechToTextTransferConfiguration audioFileTransferConfiguration,
    SpeechToTextTransferConfiguration resultTransferConfiguration,
    SpeechToTextServiceClientAuthenticationConfiguration authenticationConfiguration,
    String authorityOverride,
    int audioFileChunkSizeBytes,
    long taskObservationInitialDelayMs,
    long taskObservationMaximumDelayMs,
    double taskObservationDelayMultiplier,
    int observationThreadPoolSize) {

  public SpeechToTextServiceClientConfiguration {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("No speech-to-text-service host provided");
    }
    if (port <= 0) {
      throw new IllegalArgumentException("Invalid speech-to-text-service port provided");
    }
    if (trustedServerCertificatePath == null || !Files.isRegularFile(trustedServerCertificatePath)) {
      throw new IllegalArgumentException(
          "No trusted server certificate path provided or the specified path is not a file");
    }
    if (audioFileTransferConfiguration == null) {
      throw new IllegalArgumentException("No audio file transfer configuration provided");
    }
    if (resultTransferConfiguration == null) {
      throw new IllegalArgumentException("No result transfer configuration provided");
    }
    if (audioFileChunkSizeBytes <= 0) {
      throw new IllegalArgumentException("Audio file chunk size must be greater than zero");
    }
    if (taskObservationInitialDelayMs < 0) {
      throw new IllegalArgumentException("Task observation initial delay must not be negative");
    }
    if (taskObservationMaximumDelayMs <= 0) {
      throw new IllegalArgumentException("Task observation maximum delay must be greater than zero");
    }
    if (taskObservationDelayMultiplier < 1.0d) {
      throw new IllegalArgumentException(
          "Task observation delay multiplier must be greater than or equal to one");
    }
    if (observationThreadPoolSize <= 0) {
      throw new IllegalArgumentException("Observation thread pool size must be greater than zero");
    }
    if (taskObservationInitialDelayMs > taskObservationMaximumDelayMs) {
      throw new IllegalArgumentException(
          "Task observation initial delay must not exceed the maximum delay");
    }
    trustedServerCertificatePath = normalizePath(trustedServerCertificatePath);
    audioFileTransferConfiguration = normalizeTransferConfiguration(audioFileTransferConfiguration);
    resultTransferConfiguration = normalizeTransferConfiguration(resultTransferConfiguration);
  }

  public static SpeechToTextServiceClientConfigurationBuilder builder(
      String host,
      int port,
      Path trustedServerCertificatePath,
      SpeechToTextTransferConfiguration audioFileTransferConfiguration,
      SpeechToTextTransferConfiguration resultTransferConfiguration) {
    return new SpeechToTextServiceClientConfigurationBuilder(
        host,
        port,
        trustedServerCertificatePath,
        audioFileTransferConfiguration,
        resultTransferConfiguration);
  }

  private static Path normalizePath(Path path) {
    return path == null ? null : path.toAbsolutePath().normalize();
  }

  private static SpeechToTextTransferConfiguration normalizeTransferConfiguration(
      SpeechToTextTransferConfiguration transferConfiguration) {
    return switch (transferConfiguration.transferType()) {
      case STREAMING -> transferConfiguration;
      case SHARED_STORAGE ->
          SpeechToTextTransferConfigurationFactory.sharedStorage(
              normalizePath(transferConfiguration.sharedStorageDirectory()));
    };
  }
}
