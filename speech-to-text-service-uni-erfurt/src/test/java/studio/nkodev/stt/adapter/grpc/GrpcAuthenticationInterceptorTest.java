package studio.nkodev.stt.adapter.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.auth.AuthenticationException;
import studio.nkodev.stt.auth.CertificateAuthenticator;

/**
 * Testsuite of {@link GrpcAuthenticationInterceptor}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class GrpcAuthenticationInterceptorTest {

  private ServerCall<Object, Object> serverCall;
  private Metadata metadata;
  private ServerCallHandler<Object, Object> next;

  @BeforeEach
  public void setup() throws SSLPeerUnverifiedException {
    serverCall = mock(ServerCall.class);
    metadata = new Metadata();
    next = mock(ServerCallHandler.class);
    SSLSession sslSession = mock(SSLSession.class);
    X509Certificate certificate = mock(X509Certificate.class);
    Attributes attributes =
        Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, sslSession).build();

    when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[] {certificate});
    when(serverCall.getAttributes()).thenReturn(attributes);
  }

  @Test
  public void shouldCloseCallWithUnauthenticatedOnAuthenticationError()
      throws AuthenticationException {
    CertificateAuthenticator certificateAuthenticator = mock(CertificateAuthenticator.class);
    doThrow(new AuthenticationException("Authentication failed"))
        .when(certificateAuthenticator)
        .authenticate(any(X509Certificate[].class));

    GrpcAuthenticationInterceptor grpcAuthenticationInterceptor =
        new GrpcAuthenticationInterceptor(certificateAuthenticator);
    grpcAuthenticationInterceptor.interceptCall(serverCall, metadata, next);

    verify(serverCall, times(1))
        .close(
            argThat(
                status ->
                    status.getCode().equals(Status.UNAUTHENTICATED.getCode())
                        && "Authentication failed".equals(status.getDescription())),
            any(Metadata.class));
    verify(next, never()).startCall(any(), any());
  }

  @Test
  public void shouldStartNextCallHandlerOnSuccessfulAuthentication()
      throws AuthenticationException {
    CertificateAuthenticator certificateAuthenticator = mock(CertificateAuthenticator.class);
    ServerCall.Listener<Object> listener = new ServerCall.Listener<>() {};
    when(next.startCall(same(serverCall), same(metadata))).thenReturn(listener);

    GrpcAuthenticationInterceptor grpcAuthenticationInterceptor =
        new GrpcAuthenticationInterceptor(certificateAuthenticator);

    grpcAuthenticationInterceptor.interceptCall(serverCall, metadata, next);

    verify(certificateAuthenticator, times(1)).authenticate(any(X509Certificate[].class));
    verify(next, times(1)).startCall(same(serverCall), same(metadata));
    verify(serverCall, never()).close(any(Status.class), any(Metadata.class));
  }
}
