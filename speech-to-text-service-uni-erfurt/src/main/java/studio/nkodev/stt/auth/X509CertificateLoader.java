package studio.nkodev.stt.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Loads X509 certificates from PEM or DER encoded files.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public final class X509CertificateLoader {

  private X509CertificateLoader() {}

  public static X509Certificate load(Path certificatePath) throws IOException, CertificateException {
    try (InputStream inputStream = Files.newInputStream(certificatePath)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
    }
  }
}
