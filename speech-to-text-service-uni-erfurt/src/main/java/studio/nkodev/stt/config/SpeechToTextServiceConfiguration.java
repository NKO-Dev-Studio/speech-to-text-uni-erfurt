package studio.nkodev.stt.config;

import java.nio.file.Path;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import studio.nkodev.stt.engine.openai.config.OpenAIApiSpeechToTextEngineConfiguration;
import studio.nkodev.stt.adapter.grpc.SpeechToTextGrpcServerConfiguration;
import studio.nkodev.stt.db.DbConnectionCoordinatorConfig;
import studio.nkodev.stt.engine.whisper.config.LocalWhisperSpeechToTextEngineConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineCpuDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineGpuDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineMpsDevice;
import studio.nkodev.stt.storage.audio.AudioFileStorageConfiguration;
import studio.nkodev.stt.task.api.SpeechToTextTaskSchedulerConfiguration;

/**
 * Root application configuration loaded from YAML.
 *
 * @author Nico Kotlenga
 * @since 21.03.26
 */
public record SpeechToTextServiceConfiguration(
    AudioFileStorageConfigurationSection audioFileStorageConfigurationSection,
    ResultStorageConfigurationSection resultStorageConfigurationSection,
    DbConnectionConfigurationSection databaseConfigurationSection,
    EngineConfigurationSection engineConfigurationSection,
    TaskSchedulerConfigurationSection taskSchedulerConfigurationSection,
    AuthenticationConfigurationSection authenticationConfigurationSection,
    GrpcServerConfigurationSection grpcServerConfigurationSection) {

  public record AudioFileStorageConfigurationSection(
      Path audioFileStorageLocation, Path sharedStorageLocation)
      implements AudioFileStorageConfiguration {
    @Override
    public Path getAudioFileStorageLocation() {
      return audioFileStorageLocation;
    }

    @Override
    public Optional<Path> getSharedStorageLocation() {
      return Optional.ofNullable(sharedStorageLocation);
    }
  }

  public record ResultStorageConfigurationSection(
      Path resultDirectoryLocation, Path sharedStorageLocation) {}

  public record DbConnectionConfigurationSection(
      Path sqlFilePath, int maximumPoolSize, int minimumIdleSize, int connectionTimeoutMs)
      implements DbConnectionCoordinatorConfig {
    @Override
    public Path getSqlFilePath() {
      return sqlFilePath;
    }

    @Override
    public int getMaximumPoolSize() {
      return maximumPoolSize;
    }

    @Override
    public int getMinimumIdleSize() {
      return minimumIdleSize;
    }

    @Override
    public int getConnectionTimeoutMs() {
      return connectionTimeoutMs;
    }
  }

  public static class EngineConfigurationSection {
    private final LocalWhisperSpeechToTextEngineConfiguration
        localWhisperSpeechToTextEngineConfiguration;
    private final List<OpenAIApiSpeechToTextEngineConfiguration> openAIApiEngineConfigurations;

    public EngineConfigurationSection(
        LocalWhisperSpeechToTextEngineConfiguration localWhisperSpeechToTextEngineConfiguration,
        Collection<? extends OpenAIApiSpeechToTextEngineConfiguration>
            openAIApiEngineConfigurations) {
      this.localWhisperSpeechToTextEngineConfiguration =
          localWhisperSpeechToTextEngineConfiguration;
      this.openAIApiEngineConfigurations = List.copyOf(openAIApiEngineConfigurations);
    }

    public Optional<LocalWhisperSpeechToTextEngineConfiguration>
        getLocalWhisperSpeechToTextEngineConfiguration() {
      return Optional.ofNullable(localWhisperSpeechToTextEngineConfiguration);
    }

    public List<OpenAIApiSpeechToTextEngineConfiguration> getOpenAIApiEngineConfigurations() {
      return openAIApiEngineConfigurations;
    }
  }

  public record LocalWhisperConfigurationSection(
      Path whisperExecutable, WhisperDeviceConfigurationSection device)
      implements LocalWhisperSpeechToTextEngineConfiguration {
    @Override
    public Path getWhisperExecutable() {
      return whisperExecutable;
    }

    @Override
    public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
      switch (device.type) {
        case "mps":
          return new LocalWhisperSpeechToTextEngineMpsDevice();
        case "gpu":
          int gpuNumber = device().gpuNumber == null ? 0 : device.gpuNumber;
          return new LocalWhisperSpeechToTextEngineGpuDeviceConfiguration(gpuNumber);
        case "cpu":
          int numberOfThreads = device.numberOfThreads == null ? 0 : device.numberOfThreads;
          return new LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(numberOfThreads);
      }

      throw new IllegalStateException("Unknown device type: " + device.type);
    }
  }

  public record WhisperDeviceConfigurationSection(
      String type, Integer numberOfThreads, Integer gpuNumber) {}

  public record OpenAIApiEngineConfigurationSection(String identifier, URI apiBaseUrl, String apiToken)
      implements OpenAIApiSpeechToTextEngineConfiguration {

    @Override
    public String getIdentifier() {
      return identifier;
    }

    @Override
    public URI getApiBaseUrl() {
      return apiBaseUrl;
    }

    @Override
    public String getApiToken() {
      return apiToken;
    }
  }

  public record TaskSchedulerConfigurationSection(int numberOfParallelTasks)
      implements SpeechToTextTaskSchedulerConfiguration {
    @Override
    public int getNumberOfParallelTasks() {
      return numberOfParallelTasks;
    }
  }

  public enum AuthenticationType {
    NONE,
    CERTIFICATE;

    public static AuthenticationType fromConfigurationValue(String value) {
      return switch (value.toLowerCase()) {
        case "none" -> NONE;
        case "certificate" -> CERTIFICATE;
        default -> throw new IllegalArgumentException("Unknown authentication type: " + value);
      };
    }
  }

  public record AuthenticationConfigurationSection(
      AuthenticationType type, Path rootCertificatePath) {

    public Optional<Path> getRootCertificatePath() {
      return Optional.ofNullable(rootCertificatePath);
    }
  }

  public record GrpcServerConfigurationSection(
      int port,
      Path serverCertificatePath,
      Path serverPrivateKeyPath,
      Path serverPrivateKeyPasswordFile)
      implements SpeechToTextGrpcServerConfiguration {
    @Override
    public int getPort() {
      return port;
    }

    @Override
    public Path getServerCertificatePath() {
      return serverCertificatePath;
    }

    @Override
    public Path getServerPrivateKeyPath() {
      return serverPrivateKeyPath;
    }

    @Override
    public Optional<Path> getServerPrivateKeyPasswordPath() {
      return Optional.ofNullable(serverPrivateKeyPasswordFile);
    }
  }
}
