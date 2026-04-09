package studio.nkodev.stt.auth;

import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * Accepts every client certificate during the TLS handshake. Actual authentication happens
 * afterwards inside the adapter-independent certificate authenticator.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public class PermissiveClientTrustManager implements X509TrustManager {

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) {}

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) {}

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}
