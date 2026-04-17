package studio.nkodev.stt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testsuite of {@link SpeechToTextServiceConfigurationLoader}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 21.03.26
 */
public class SpeechToTextServiceConfigurationLoaderTest {

  @TempDir Path tempDirectory;

  @Test
  void loadShouldReadNoAuthenticationConfiguration() throws IOException {
    Path configurationFile =
        Files.writeString(
            tempDirectory.resolve("config-none.yaml"),
            """
            audioFileStorage:
              audioFileStorageLocation: "/tmp/audio"
              sharedStorageLocation: "/tmp/audio-shared"

            resultStorage:
              resultDirectoryLocation: "/tmp/results"
              sharedStorageLocation: "/tmp/results-shared"

            database:
              sqlFilePath: "/tmp/speech-to-text.sqlite"
              maximumPoolSize: 4
              minimumIdleSize: 1
              connectionTimeoutMs: 30000

            engines:
              localWhisper:
                whisperExecutable: "/tmp/whisper-cli"
                device:
                  type: "cpu"
                  numberOfThreads: 4

            taskScheduler:
              numberOfParallelTasks: 2

            authentication:
              type: "none"

            grpcServer:
              port: 8080
              serverCertificatePath: "/tmp/server.pem"
              serverPrivateKeyPath: "/tmp/server.key"
            """);

    SpeechToTextServiceConfiguration configuration =
        SpeechToTextServiceConfigurationLoader.load(configurationFile);

    assertEquals(
        SpeechToTextServiceConfiguration.AuthenticationType.NONE,
        configuration.authenticationConfigurationSection().type());
    assertTrue(
        configuration.authenticationConfigurationSection().getRootCertificatePath().isEmpty());
  }

  @Test
  void loadShouldReadCertificateAuthenticationConfiguration() throws IOException {
    Path configurationFile =
        Files.writeString(
            tempDirectory.resolve("config-certificate.yaml"),
            """
            audioFileStorage:
              audioFileStorageLocation: "/tmp/audio"
              sharedStorageLocation: "/tmp/audio-shared"

            resultStorage:
              resultDirectoryLocation: "/tmp/results"
              sharedStorageLocation: "/tmp/results-shared"

            database:
              sqlFilePath: "/tmp/speech-to-text.sqlite"
              maximumPoolSize: 4
              minimumIdleSize: 1
              connectionTimeoutMs: 30000

            engines:
              localWhisper:
                whisperExecutable: "/tmp/whisper-cli"
                device:
                  type: "cpu"
                  numberOfThreads: 4

            taskScheduler:
              numberOfParallelTasks: 2

            authentication:
              type: "certificate"
              rootCertificatePath: "/tmp/root.pem"

            grpcServer:
              port: 8080
              serverCertificatePath: "/tmp/server.pem"
              serverPrivateKeyPath: "/tmp/server.key"
            """);

    SpeechToTextServiceConfiguration configuration =
        SpeechToTextServiceConfigurationLoader.load(configurationFile);

    assertEquals(
        SpeechToTextServiceConfiguration.AuthenticationType.CERTIFICATE,
        configuration.authenticationConfigurationSection().type());
    assertNotNull(
        configuration.authenticationConfigurationSection().getRootCertificatePath().orElse(null));
    assertEquals(
        Path.of("/tmp/root.pem"),
        configuration.authenticationConfigurationSection().getRootCertificatePath().orElseThrow());
    assertEquals(
        Path.of("/tmp/server.pem"),
        configuration.grpcServerConfigurationSection().getServerCertificatePath());
  }

  @Test
  void loadShouldReadOpenAIApiEngineConfigurations() throws IOException {
    Path configurationFile =
        Files.writeString(
            tempDirectory.resolve("config-openai.yaml"),
            """
            audioFileStorage:
              audioFileStorageLocation: "/tmp/audio"

            resultStorage:
              resultDirectoryLocation: "/tmp/results"

            database:
              sqlFilePath: "/tmp/speech-to-text.sqlite"
              maximumPoolSize: 4
              minimumIdleSize: 1
              connectionTimeoutMs: 30000

            engines:
              openAIApiEngines:
                - identifier: "external-whisper-a"
                  apiBaseUrl: "https://api-a.example.com"
                  apiToken: "token-a"
                - identifier: "external-whisper-b"
                  apiBaseUrl: "https://api-b.example.com/"
                  apiToken: "token-b"

            taskScheduler:
              numberOfParallelTasks: 2

            authentication:
              type: "none"

            grpcServer:
              port: 8080
              serverCertificatePath: "/tmp/server.pem"
              serverPrivateKeyPath: "/tmp/server.key"
            """);

    SpeechToTextServiceConfiguration configuration =
        SpeechToTextServiceConfigurationLoader.load(configurationFile);

    assertTrue(
        configuration
            .engineConfigurationSection()
            .getLocalWhisperSpeechToTextEngineConfiguration()
            .isEmpty());
    assertEquals(
        2, configuration.engineConfigurationSection().getOpenAIApiEngineConfigurations().size());
    assertEquals(
        "external-whisper-a",
        configuration
            .engineConfigurationSection()
            .getOpenAIApiEngineConfigurations()
            .getFirst()
            .getIdentifier());
    assertEquals(
        URI.create("https://api-a.example.com"),
        configuration
            .engineConfigurationSection()
            .getOpenAIApiEngineConfigurations()
            .getFirst()
            .getApiBaseUrl());
  }
}
