package studio.nkodev.stt.client.config;

import java.nio.file.Path;

/**
 * Builder creating {@link SpeechToTextServiceClientAuthenticationConfiguration} instances.
 *
 * <p>The constructor defines the required client certificate and matching private key used for
 * mutual TLS authentication. The private key password is optional.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 04.04.26
 */
public class SpeechToTextServiceClientAuthenticationConfigurationBuilder {

  private final Path certificatePath;
  private final Path privateKeyPath;
  private String privateKeyPassword;

  /**
   * Creates a builder with the mandatory mutual TLS authentication settings.
   *
   * @param certificatePath path to the client certificate file
   * @param privateKeyPath path to the matching client private key file
   */
  public SpeechToTextServiceClientAuthenticationConfigurationBuilder(
      Path certificatePath, Path privateKeyPath) {
    this.certificatePath = certificatePath;
    this.privateKeyPath = privateKeyPath;
  }

  /**
   * Sets the password used to unlock the configured client private key, if the key file is
   * encrypted.
   *
   * <p>Default: no private key password is configured.
   *
   * @param privateKeyPassword password of the client private key
   */
  public SpeechToTextServiceClientAuthenticationConfigurationBuilder privateKeyPassword(
      String privateKeyPassword) {
    this.privateKeyPassword = privateKeyPassword;
    return this;
  }

  /**
   * Creates the immutable {@link SpeechToTextServiceClientAuthenticationConfiguration} instance.
   *
   * @return built authentication configuration
   */
  public SpeechToTextServiceClientAuthenticationConfiguration build() {
    return new SpeechToTextServiceClientAuthenticationConfiguration(
        certificatePath, privateKeyPath, privateKeyPassword);
  }
}
