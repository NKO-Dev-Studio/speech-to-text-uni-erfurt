package studio.nkodev.stt.auth;

import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Authenticates client certificate chains against a configured root certificate.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public class CertificateAuthenticator {

  private final CertificateFactory certificateFactory;
  private final TrustAnchor trustAnchor;

  public CertificateAuthenticator(X509Certificate rootCertificate)
      throws CertificateException {
    this.certificateFactory = CertificateFactory.getInstance("X.509");
    this.trustAnchor = new TrustAnchor(rootCertificate, null);
  }

  public void authenticate(X509Certificate[] certificateChain) throws AuthenticationException {
    if (certificateChain == null || certificateChain.length == 0) {
      throw new AuthenticationException("Missing client certificate");
    }

    List<X509Certificate> certificatePath = Arrays.asList(certificateChain);
    try {
      CertPath certPath = certificateFactory.generateCertPath(certificatePath);
      PKIXParameters pkixParameters = new PKIXParameters(Set.of(trustAnchor));
      pkixParameters.setRevocationEnabled(false);
      CertPathValidator.getInstance("PKIX").validate(certPath, pkixParameters);
    } catch (GeneralSecurityException ex) {
      throw new AuthenticationException("Invalid client certificate", ex);
    }
  }
}
