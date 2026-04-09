package studio.nkodev.stt.adapter.grpc;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptors;
import io.grpc.TlsServerCredentials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.auth.CertificateAuthenticator;
import studio.nkodev.stt.auth.PermissiveClientTrustManager;
import studio.nkodev.stt.service.SpeechToTextService;
import studio.nkodev.stt.storage.audio.SharedStorageAudioFileProviderFactory;

/**
 * Server opens an gRPC interface which can be used by client to trigger speech to text tasks. TODO:
 * Improve connection timeouts TODO: Define how to handle connection closes
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 14.03.26
 */
public class SpeechToTextGrpcServer {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextGrpcServer.class);

  private final Server server;
  private final SpeechToTextGrpcServerConfiguration configuration;

  public SpeechToTextGrpcServer(
      SpeechToTextGrpcServerConfiguration configuration,
      CertificateAuthenticator certificateAuthenticator,
      SpeechToTextService speechToTextService,
      SharedStorageAudioFileProviderFactory sharedStorageAudioFileProviderFactory) {
    SpeechToTextGrpcService grpcService =
        new SpeechToTextGrpcService(speechToTextService, sharedStorageAudioFileProviderFactory);
    this.server =
        Grpc.newServerBuilderForPort(
                configuration.getPort(),
                createServerCredentials(configuration, certificateAuthenticator != null))
            .addService(createServiceDefinition(certificateAuthenticator, grpcService))
            .build();
    this.configuration = configuration;
  }

  public void start() throws IOException {
    logger.info("Starting gRPC interface listening on port {}", configuration.getPort());
    server.start();
  }

  /** Stop serving requests and shutdown resources. */
  public void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private static ServerCredentials createServerCredentials(
      SpeechToTextGrpcServerConfiguration configuration, boolean authenticationEnabled) {
    try {
      Path serverCertificatePath = configuration.getServerCertificatePath();
      Path serverPrivateKeyPath = configuration.getServerPrivateKeyPath();
      Optional<Path> serverPrivateKeyPasswordPath = configuration.getServerPrivateKeyPasswordPath();
      String privateKeyPassword = null;
      if (serverPrivateKeyPasswordPath.isPresent()) {
        privateKeyPassword = Files.readString(serverPrivateKeyPasswordPath.get());
      }

      return TlsServerCredentials.newBuilder()
          .keyManager(
              serverCertificatePath.toFile(), serverPrivateKeyPath.toFile(), privateKeyPassword)
          .trustManager(new PermissiveClientTrustManager())
          .clientAuth(
              authenticationEnabled
                  ? TlsServerCredentials.ClientAuth.OPTIONAL
                  : TlsServerCredentials.ClientAuth.NONE)
          .build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to initialize gRPC TLS credentials", ex);
    }
  }

  private static io.grpc.ServerServiceDefinition createServiceDefinition(
      CertificateAuthenticator certificateAuthenticator, SpeechToTextGrpcService grpcService) {
    if (certificateAuthenticator == null) {
      return grpcService.bindService();
    }
    return ServerInterceptors.intercept(
        grpcService, new GrpcAuthenticationInterceptor(certificateAuthenticator));
  }
}
