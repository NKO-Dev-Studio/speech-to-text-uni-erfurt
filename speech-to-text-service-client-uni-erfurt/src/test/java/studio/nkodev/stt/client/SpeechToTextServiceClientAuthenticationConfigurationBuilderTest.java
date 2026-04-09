package studio.nkodev.stt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientAuthenticationConfiguration;

class SpeechToTextServiceClientAuthenticationConfigurationBuilderTest {

  @TempDir private Path tempDir;

  @Test
  void shouldBuildAuthenticationConfiguration() throws Exception {
    Path certificatePath = Files.createFile(tempDir.resolve("client.pem"));
    Path privateKeyPath = Files.createFile(tempDir.resolve("client.key"));

    SpeechToTextServiceClientAuthenticationConfiguration authenticationConfiguration =
        SpeechToTextServiceClientAuthenticationConfiguration.builder(certificatePath, privateKeyPath)
            .privateKeyPassword("secret")
            .build();

    assertEquals(certificatePath.toAbsolutePath(), authenticationConfiguration.certificatePath());
    assertEquals(privateKeyPath.toAbsolutePath(), authenticationConfiguration.privateKeyPath());
    assertEquals("secret", authenticationConfiguration.privateKeyPassword());
  }

  @Test
  void shouldRequireExistingAuthenticationCertificatePath() throws Exception {
    Path privateKeyPath = Files.createFile(tempDir.resolve("client.key"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeechToTextServiceClientAuthenticationConfiguration.builder(
                    tempDir.resolve("missing.pem"), privateKeyPath)
                .build());
  }

  @Test
  void shouldRequireExistingAuthenticationPrivateKeyPath() throws Exception {
    Path certificatePath = Files.createFile(tempDir.resolve("client.pem"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeechToTextServiceClientAuthenticationConfiguration.builder(
                    certificatePath, tempDir.resolve("missing.key"))
                .build());
  }
}
