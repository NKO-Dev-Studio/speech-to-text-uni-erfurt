package studio.nkodev.stt.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import studio.nkodev.stt.config.SpeechToTextServiceConfiguration.LocalWhisperConfigurationSection;

/**
 * Loads the application configuration from YAML.
 *
 * @author Nico Kotlenga
 * @since 21.03.26
 */
public final class SpeechToTextServiceConfigurationLoader {

  private SpeechToTextServiceConfigurationLoader() {}

  public static SpeechToTextServiceConfiguration load(Path configurationFilePath)
      throws IOException {
    try (InputStream inputStream = Files.newInputStream(configurationFilePath)) {
      Object loadedConfiguration = new Yaml().load(inputStream);
      if (!(loadedConfiguration instanceof Map<?, ?> configurationMap)) {
        throw new IllegalArgumentException("Configuration file must contain a YAML object at root");
      }

      return new SpeechToTextServiceConfiguration(
          readAudioFileStorageConfigurationSection(
              requireMap(configurationMap, "audioFileStorage")),
          readResultStorageConfigurationSection(requireMap(configurationMap, "resultStorage")),
          readDbConnectionConfigurationSection(requireMap(configurationMap, "database")),
          readEngineConfigurationSection(requireMap(configurationMap, "engines")),
          readTaskSchedulerConfigurationSection(requireMap(configurationMap, "taskScheduler")),
          readAuthenticationConfigurationSection(requireMap(configurationMap, "authentication")),
          readGrpcServerConfigurationSection(requireMap(configurationMap, "grpcServer")));
    }
  }

  private static SpeechToTextServiceConfiguration.EngineConfigurationSection
      readEngineConfigurationSection(Map<?, ?> configurationMap) {
    Map<?, ?> localWhisperConfigurationMap = optionalMap(configurationMap, "localWhisper");
    LocalWhisperConfigurationSection localWhisperConfigurationSection = null;
    if (localWhisperConfigurationMap != null) {
      localWhisperConfigurationSection =
          readLocalWhisperConfigurationSection(localWhisperConfigurationMap);
    }

    Collection<SpeechToTextServiceConfiguration.OpenAIApiEngineConfigurationSection>
        openAIApiEngineConfigurations =
            readOpenAIApiEngineConfigurationSections(configurationMap, "openAIApiEngines");

    return new SpeechToTextServiceConfiguration.EngineConfigurationSection(
        localWhisperConfigurationSection, openAIApiEngineConfigurations);
  }

  private static SpeechToTextServiceConfiguration.AudioFileStorageConfigurationSection
      readAudioFileStorageConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.AudioFileStorageConfigurationSection(
        readPath(configurationMap, "audioFileStorageLocation"),
        readOptionalPath(configurationMap, "sharedStorageLocation"));
  }

  private static SpeechToTextServiceConfiguration.ResultStorageConfigurationSection
      readResultStorageConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.ResultStorageConfigurationSection(
        readPath(configurationMap, "resultDirectoryLocation"),
        readOptionalPath(configurationMap, "sharedStorageLocation"));
  }

  private static SpeechToTextServiceConfiguration.DbConnectionConfigurationSection
      readDbConnectionConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.DbConnectionConfigurationSection(
        readPath(configurationMap, "sqlFilePath"),
        readInt(configurationMap, "maximumPoolSize"),
        readInt(configurationMap, "minimumIdleSize"),
        readInt(configurationMap, "connectionTimeoutMs"));
  }

  private static LocalWhisperConfigurationSection readLocalWhisperConfigurationSection(
      Map<?, ?> configurationMap) {
    return new LocalWhisperConfigurationSection(
        readPath(configurationMap, "whisperExecutable"),
        readWhisperDeviceConfigurationSection(requireMap(configurationMap, "device")));
  }

  private static SpeechToTextServiceConfiguration.WhisperDeviceConfigurationSection
      readWhisperDeviceConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.WhisperDeviceConfigurationSection(
        readString(configurationMap, "type"),
        readOptionalInt(configurationMap, "numberOfThreads"),
        readOptionalInt(configurationMap, "gpuNumber"));
  }

  private static SpeechToTextServiceConfiguration.TaskSchedulerConfigurationSection
      readTaskSchedulerConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.TaskSchedulerConfigurationSection(
        readInt(configurationMap, "numberOfParallelTasks"));
  }

  private static SpeechToTextServiceConfiguration.AuthenticationConfigurationSection
      readAuthenticationConfigurationSection(Map<?, ?> configurationMap) {
    SpeechToTextServiceConfiguration.AuthenticationType authenticationType =
        SpeechToTextServiceConfiguration.AuthenticationType.fromConfigurationValue(
            readString(configurationMap, "type"));

    Path rootCertificatePath =
        authenticationType == SpeechToTextServiceConfiguration.AuthenticationType.CERTIFICATE
            ? readPath(configurationMap, "rootCertificatePath")
            : readOptionalPath(configurationMap, "rootCertificatePath");

    return new SpeechToTextServiceConfiguration.AuthenticationConfigurationSection(
        authenticationType, rootCertificatePath);
  }

  private static SpeechToTextServiceConfiguration.GrpcServerConfigurationSection
      readGrpcServerConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.GrpcServerConfigurationSection(
        readInt(configurationMap, "port"),
        readPath(configurationMap, "serverCertificatePath"),
        readPath(configurationMap, "serverPrivateKeyPath"),
        readOptionalPath(configurationMap, "serverPrivateKeyPasswordFile"));
  }

  private static Map<?, ?> requireMap(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (!(value instanceof Map<?, ?> valueMap)) {
      throw new IllegalArgumentException("Missing configuration section: " + key);
    }
    return valueMap;
  }

  private static Map<?, ?> optionalMap(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> valueMap)) {
      throw new IllegalArgumentException("Invalid configuration section: " + key);
    }
    return valueMap;
  }

  private static Collection<SpeechToTextServiceConfiguration.OpenAIApiEngineConfigurationSection>
      readOpenAIApiEngineConfigurationSections(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> listValue)) {
      throw new IllegalArgumentException("Invalid configuration section: " + key);
    }

    List<SpeechToTextServiceConfiguration.OpenAIApiEngineConfigurationSection> configurations =
        new ArrayList<>();
    for (Object currentValue : listValue) {
      if (!(currentValue instanceof Map<?, ?> entryMap)) {
        throw new IllegalArgumentException("Invalid configuration value in section: " + key);
      }
      configurations.add(readOpenAIApiEngineConfigurationSection(entryMap));
    }
    return configurations;
  }

  private static SpeechToTextServiceConfiguration.OpenAIApiEngineConfigurationSection
      readOpenAIApiEngineConfigurationSection(Map<?, ?> configurationMap) {
    return new SpeechToTextServiceConfiguration.OpenAIApiEngineConfigurationSection(
        readString(configurationMap, "identifier"),
        URI.create(readString(configurationMap, "apiBaseUrl")),
        readString(configurationMap, "apiToken"));
  }

  private static Path readPath(Map<?, ?> configurationMap, String key) {
    return Path.of(readString(configurationMap, key));
  }

  private static Path readOptionalPath(Map<?, ?> configurationMap, String key) {
    String pathString = readOptionalString(configurationMap, key);
    if (pathString == null) {
      return null;
    }

    return Path.of(pathString);
  }

  private static String readString(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw new IllegalArgumentException("Missing or invalid configuration value: " + key);
    }
    return stringValue;
  }

  private static String readOptionalString(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (value == null) {
      return null;
    }

    if (!(value instanceof String stringValue)) {
      throw new IllegalArgumentException("Missing or invalid configuration value: " + key);
    }
    return stringValue;
  }

  private static int readInt(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (!(value instanceof Number numberValue)) {
      throw new IllegalArgumentException("Missing or invalid configuration value: " + key);
    }
    return numberValue.intValue();
  }

  private static Integer readOptionalInt(Map<?, ?> configurationMap, String key) {
    Object value = configurationMap.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number numberValue)) {
      throw new IllegalArgumentException("Invalid configuration value: " + key);
    }
    return numberValue.intValue();
  }
}
