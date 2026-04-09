package studio.nkodev.stt.client.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mutual TLS authentication configuration of the speech-to-text client.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 04.04.26
 */
public record SpeechToTextServiceClientAuthenticationConfiguration(
    Path certificatePath, Path privateKeyPath, String privateKeyPassword) {

  public SpeechToTextServiceClientAuthenticationConfiguration {
    if (certificatePath == null || !Files.isRegularFile(certificatePath)) {
      throw new IllegalArgumentException(
          "The specified authentication certificate path is not an existing file");
    }
    if (privateKeyPath == null || !Files.isRegularFile(privateKeyPath)) {
      throw new IllegalArgumentException(
          "The specified authentication private key path is not an existing file");
    }

    certificatePath = normalizePath(certificatePath);
    privateKeyPath = normalizePath(privateKeyPath);
  }

  public static SpeechToTextServiceClientAuthenticationConfigurationBuilder builder(
      Path certificatePath, Path privateKeyPath) {
    return new SpeechToTextServiceClientAuthenticationConfigurationBuilder(
        certificatePath, privateKeyPath);
  }

  private static Path normalizePath(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
