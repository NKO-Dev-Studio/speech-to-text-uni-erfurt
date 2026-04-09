package studio.nkodev.stt.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testsuite of {@link CertificateAuthenticator}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class CertificateAuthenticatorTest {

  private static final String ROOT_CA_CERTIFICATE = "root-ca.cert.pem";
  private static final String CLIENT_CERTIFICATE = "speech-to-text-client.cert.pem";

  private CertificateAuthenticator certificateAuthenticator;
  private X509Certificate clientCertificate;

  @BeforeEach
  public void setUp() throws CertificateException, IOException, URISyntaxException {
    X509Certificate rootCertificate = loadCertificate(ROOT_CA_CERTIFICATE);
    clientCertificate = loadCertificate(CLIENT_CERTIFICATE);
    certificateAuthenticator = new CertificateAuthenticator(rootCertificate);
  }

  @Test
  public void shouldAuthenticateSuccessfully() {
    assertDoesNotThrow(
        () -> certificateAuthenticator.authenticate(new X509Certificate[] {clientCertificate}));
  }

  @Test
  public void shouldNotAuthenticateSuccessfullyBecauseOfEmptyCertificateChain() {
    AuthenticationException authenticationException =
        assertThrows(
            AuthenticationException.class,
            () -> certificateAuthenticator.authenticate(new X509Certificate[0]));

    assertEquals("Missing client certificate", authenticationException.getMessage());
  }

  @Test
  public void shouldNotAuthenticateSuccessfullyBecauseOfInvalidCertificate() {
    AuthenticationException authenticationException =
        assertThrows(
            AuthenticationException.class,
            () ->
                certificateAuthenticator.authenticate(
                    new X509Certificate[] {
                      loadCertificate(ROOT_CA_CERTIFICATE), clientCertificate
                    }));

    assertEquals("Invalid client certificate", authenticationException.getMessage());
  }

  private static X509Certificate loadCertificate(String certificateName)
      throws IOException, CertificateException, URISyntaxException {
    Path certificatePath =
        Path.of(
            Objects.requireNonNull(
                    CertificateAuthenticatorTest.class.getResource(
                        "/studio/nkodev/stt/adapter/grpc/" + certificateName))
                .toURI());
    return X509CertificateLoader.load(certificatePath);
  }
}
