package studio.nkodev.stt.adapter.grpc;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import studio.nkodev.stt.auth.AuthenticationException;
import studio.nkodev.stt.auth.CertificateAuthenticator;

/**
 * Enforces client certificate authentication for gRPC requests.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public class GrpcAuthenticationInterceptor implements ServerInterceptor {

  private final CertificateAuthenticator certificateAuthenticator;

  public GrpcAuthenticationInterceptor(CertificateAuthenticator certificateAuthenticator) {
    this.certificateAuthenticator = certificateAuthenticator;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {
    try {
      certificateAuthenticator.authenticate(readClientCertificateChain(call));
    } catch (AuthenticationException ex) {
      call.close(Status.UNAUTHENTICATED.withDescription(ex.getMessage()), new Metadata());
      return new ServerCall.Listener<>() {};
    }

    return next.startCall(call, headers);
  }

  private static X509Certificate[] readClientCertificateChain(ServerCall<?, ?> call)
      throws AuthenticationException {
    SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
    if (sslSession == null) {
      throw new AuthenticationException("Missing TLS session");
    }

    try {
      Certificate[] peerCertificates = sslSession.getPeerCertificates();
      X509Certificate[] x509Certificates = new X509Certificate[peerCertificates.length];
      for (int i = 0; i < peerCertificates.length; i++) {
        if (!(peerCertificates[i] instanceof X509Certificate x509Certificate)) {
          throw new AuthenticationException("Unsupported client certificate type");
        }
        x509Certificates[i] = x509Certificate;
      }
      return x509Certificates;
    } catch (SSLPeerUnverifiedException ex) {
      throw new AuthenticationException("Missing client certificate", ex);
    }
  }
}
