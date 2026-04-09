package studio.nkodev.stt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientAuthenticationConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;
import studio.nkodev.stt.client.config.SpeechToTextTransferType;

class SpeechToTextServiceClientConfigurationBuilderTest {

  @TempDir private Path tempDir;

  @Test
  void shouldBuildConfigurationWithRequiredConstructorArguments() throws Exception {
    Path trustedServerCertificatePath = Files.createFile(tempDir.resolve("server.pem"));
    SpeechToTextServiceClientConfiguration configuration =
        SpeechToTextServiceClientConfiguration.builder(
                "localhost",
                8443,
                trustedServerCertificatePath,
                SpeechToTextTransferConfigurationFactory.streaming(),
                SpeechToTextTransferConfigurationFactory.streaming())
            .build();

    assertEquals("localhost", configuration.host());
    assertEquals(8443, configuration.port());
    assertEquals(
        SpeechToTextTransferType.STREAMING,
        configuration.audioFileTransferConfiguration().transferType());
    assertEquals(
        SpeechToTextTransferType.STREAMING,
        configuration.resultTransferConfiguration().transferType());
    assertEquals(64 * 1024, configuration.audioFileChunkSizeBytes());
  }

  @Test
  void shouldRequireTrustedServerCertificatePath() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeechToTextServiceClientConfiguration.builder(
                    "localhost",
                    8443,
                    tempDir.resolve("missing.pem"),
                    SpeechToTextTransferConfigurationFactory.streaming(),
                    SpeechToTextTransferConfigurationFactory.streaming())
                .build());
  }

  @Test
  void shouldAcceptSharedStorageDirectoriesWhenConfigured() throws Exception {
    Path audioSharedStorage = Files.createDirectory(tempDir.resolve("audio-shared"));
    Path resultSharedStorage = Files.createDirectory(tempDir.resolve("result-shared"));
    Path trustedServerCertificatePath = Files.createFile(tempDir.resolve("server.pem"));

    SpeechToTextServiceClientConfiguration configuration =
        SpeechToTextServiceClientConfiguration.builder(
                "localhost",
                8443,
                trustedServerCertificatePath,
                SpeechToTextTransferConfigurationFactory.sharedStorage(audioSharedStorage),
                SpeechToTextTransferConfigurationFactory.sharedStorage(resultSharedStorage))
            .build();

    assertEquals(
        audioSharedStorage.toAbsolutePath(),
        configuration.audioFileTransferConfiguration().sharedStorageDirectory());
    assertEquals(
        resultSharedStorage.toAbsolutePath(),
        configuration.resultTransferConfiguration().sharedStorageDirectory());
  }

  @Test
  void shouldAcceptAuthenticationConfigurationWhenConfigured() throws Exception {
    Path trustedServerCertificatePath = Files.createFile(tempDir.resolve("server.pem"));
    Path clientCertificatePath = Files.createFile(tempDir.resolve("client.pem"));
    Path clientPrivateKeyPath = Files.createFile(tempDir.resolve("client.key"));

    SpeechToTextServiceClientConfiguration configuration =
        SpeechToTextServiceClientConfiguration.builder(
                "localhost",
                8443,
                trustedServerCertificatePath,
                SpeechToTextTransferConfigurationFactory.streaming(),
                SpeechToTextTransferConfigurationFactory.streaming())
            .authenticationConfiguration(
                SpeechToTextServiceClientAuthenticationConfiguration.builder(
                        clientCertificatePath, clientPrivateKeyPath)
                    .privateKeyPassword("secret")
                    .build())
            .build();

    assertEquals(
        clientCertificatePath.toAbsolutePath(),
        configuration.authenticationConfiguration().certificatePath());
    assertEquals(
        clientPrivateKeyPath.toAbsolutePath(),
        configuration.authenticationConfiguration().privateKeyPath());
    assertEquals("secret", configuration.authenticationConfiguration().privateKeyPassword());
  }
}
