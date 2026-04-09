package studio.nkodev.stt.client.config;

import java.nio.file.Path;

/**
 * Builder creating {@link SpeechToTextServiceClientConfiguration} instances.
 *
 * <p>The constructor defines all mandatory parameters required to connect to the
 * speech-to-text-service:
 *
 * <ul>
 *   <li>{@code host}: network host name or IP address of the speech-to-text-service
 *   <li>{@code port}: TCP port of the speech-to-text-service gRPC endpoint
 *   <li>{@code trustedServerCertificatePath}: certificate or CA certificate used to verify the
 *       server TLS certificate
 *   <li>{@code audioFileTransferConfiguration}: transfer strategy used when uploading audio files.
 *       Can be created by using the {@link SpeechToTextTransferConfigurationFactory}.
 *   <li>{@code resultTransferConfiguration}: transfer strategy used when retrieving task results.
 *       Can be created by using the {@link SpeechToTextTransferConfigurationFactory}.
 * </ul>
 *
 * <p>All remaining settings are optional and can be adjusted through the fluent setter methods.
 * Defaults:
 *
 * <ul>
 *   <li>{@code audioFileChunkSizeBytes}: {@value #DEFAULT_CHUNK_SIZE_BYTES} bytes
 *   <li>{@code taskObservationInitialDelayMs}: {@value #DEFAULT_TASK_OBSERVATION_INITIAL_DELAY_MS}
 *       ms
 *   <li>{@code taskObservationMaximumDelayMs}: {@value #DEFAULT_TASK_OBSERVATION_MAXIMUM_DELAY_MS}
 *       ms
 *   <li>{@code taskObservationDelayMultiplier}: {@value #DEFAULT_TASK_OBSERVATION_DELAY_MULTIPLIER}
 *   <li>{@code observationThreadPoolSize}: {@value #DEFAULT_OBSERVATION_THREAD_POOL_SIZE}
 * </ul>
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public class SpeechToTextServiceClientConfigurationBuilder {

  private static final int DEFAULT_CHUNK_SIZE_BYTES = 64 * 1024;
  private static final long DEFAULT_TASK_OBSERVATION_INITIAL_DELAY_MS = 250L;
  private static final long DEFAULT_TASK_OBSERVATION_MAXIMUM_DELAY_MS = 5_000L;
  private static final double DEFAULT_TASK_OBSERVATION_DELAY_MULTIPLIER = 2.0d;
  private static final int DEFAULT_OBSERVATION_THREAD_POOL_SIZE = 1;

  private final String host;
  private final int port;
  private final Path trustedServerCertificatePath;
  private final SpeechToTextTransferConfiguration audioFileTransferConfiguration;
  private final SpeechToTextTransferConfiguration resultTransferConfiguration;
  private SpeechToTextServiceClientAuthenticationConfiguration authenticationConfiguration;
  private String authorityOverride;
  private int audioFileChunkSizeBytes = DEFAULT_CHUNK_SIZE_BYTES;
  private long taskObservationInitialDelayMs = DEFAULT_TASK_OBSERVATION_INITIAL_DELAY_MS;
  private long taskObservationMaximumDelayMs = DEFAULT_TASK_OBSERVATION_MAXIMUM_DELAY_MS;
  private double taskObservationDelayMultiplier = DEFAULT_TASK_OBSERVATION_DELAY_MULTIPLIER;
  private int observationThreadPoolSize = DEFAULT_OBSERVATION_THREAD_POOL_SIZE;

  /**
   * Creates a builder with the mandatory client settings.
   *
   * @param host network host name or IP address of the speech-to-text-service
   * @param port TCP port of the speech-to-text-service gRPC endpoint
   * @param trustedServerCertificatePath certificate or CA certificate used to verify the server
   *     identity during TLS handshake
   * @param audioFileTransferConfiguration transfer strategy used to upload task audio files
   * @param resultTransferConfiguration transfer strategy used to receive task results
   */
  public SpeechToTextServiceClientConfigurationBuilder(
      String host,
      int port,
      Path trustedServerCertificatePath,
      SpeechToTextTransferConfiguration audioFileTransferConfiguration,
      SpeechToTextTransferConfiguration resultTransferConfiguration) {
    this.host = host;
    this.port = port;
    this.trustedServerCertificatePath = trustedServerCertificatePath;
    this.audioFileTransferConfiguration = audioFileTransferConfiguration;
    this.resultTransferConfiguration = resultTransferConfiguration;
  }

  /**
   * Sets the client authentication configuration used for mutual TLS authentication.
   *
   * <p>Default: no client authentication is configured.
   *
   * @param authenticationConfiguration mutual TLS client authentication configuration
   */
  public SpeechToTextServiceClientConfigurationBuilder authenticationConfiguration(
      SpeechToTextServiceClientAuthenticationConfiguration authenticationConfiguration) {
    this.authenticationConfiguration = authenticationConfiguration;
    return this;
  }

  /**
   * Overrides the authority used for TLS hostname verification and the HTTP/2 authority header.
   *
   * <p>This is useful when the network address used to connect to the service differs from the host
   * name contained in the service certificate.
   *
   * <p>Default: no override is configured, so the configured host is used.
   *
   * @param authorityOverride authority value used by the gRPC channel
   */
  public SpeechToTextServiceClientConfigurationBuilder authorityOverride(String authorityOverride) {
    this.authorityOverride = authorityOverride;
    return this;
  }

  /**
   * Sets the chunk size used when audio files are uploaded via streaming instead of shared storage.
   *
   * <p>Default: {@value #DEFAULT_CHUNK_SIZE_BYTES} bytes.
   *
   * @param audioFileChunkSizeBytes size in bytes of a streamed audio chunk
   */
  public SpeechToTextServiceClientConfigurationBuilder audioFileChunkSizeBytes(
      int audioFileChunkSizeBytes) {
    this.audioFileChunkSizeBytes = audioFileChunkSizeBytes;
    return this;
  }

  /**
   * Sets the initial delay between task state polling requests.
   *
   * <p>Default: {@value #DEFAULT_TASK_OBSERVATION_INITIAL_DELAY_MS} ms.
   *
   * @param taskObservationInitialDelayMs delay in milliseconds before the next state poll after a
   *     detected state change
   */
  public SpeechToTextServiceClientConfigurationBuilder taskObservationInitialDelayMs(
      long taskObservationInitialDelayMs) {
    this.taskObservationInitialDelayMs = taskObservationInitialDelayMs;
    return this;
  }

  /**
   * Sets the maximum delay between task state polling requests.
   *
   * <p>Default: {@value #DEFAULT_TASK_OBSERVATION_MAXIMUM_DELAY_MS} ms.
   *
   * @param taskObservationMaximumDelayMs upper polling delay in milliseconds used while a task
   *     state remains unchanged
   */
  public SpeechToTextServiceClientConfigurationBuilder taskObservationMaximumDelayMs(
      long taskObservationMaximumDelayMs) {
    this.taskObservationMaximumDelayMs = taskObservationMaximumDelayMs;
    return this;
  }

  /**
   * Sets the factor used to increase the polling delay when the observed task state did not change.
   *
   * <p>Default: {@value #DEFAULT_TASK_OBSERVATION_DELAY_MULTIPLIER}.
   *
   * @param taskObservationDelayMultiplier multiplier applied to the current observation delay
   */
  public SpeechToTextServiceClientConfigurationBuilder taskObservationDelayMultiplier(
      double taskObservationDelayMultiplier) {
    this.taskObservationDelayMultiplier = taskObservationDelayMultiplier;
    return this;
  }

  /**
   * Sets the number of threads used by the scheduled executor that performs task state
   * observations.
   *
   * <p>Default: {@value #DEFAULT_OBSERVATION_THREAD_POOL_SIZE}.
   *
   * @param observationThreadPoolSize number of observation threads
   */
  public SpeechToTextServiceClientConfigurationBuilder observationThreadPoolSize(
      int observationThreadPoolSize) {
    this.observationThreadPoolSize = observationThreadPoolSize;
    return this;
  }

  /**
   * Creates the immutable {@link SpeechToTextServiceClientConfiguration} instance.
   *
   * @return built client configuration
   */
  public SpeechToTextServiceClientConfiguration build() {
    return new SpeechToTextServiceClientConfiguration(
        host,
        port,
        trustedServerCertificatePath,
        audioFileTransferConfiguration,
        resultTransferConfiguration,
        authenticationConfiguration,
        authorityOverride,
        audioFileChunkSizeBytes,
        taskObservationInitialDelayMs,
        taskObservationMaximumDelayMs,
        taskObservationDelayMultiplier,
        observationThreadPoolSize);
  }
}
