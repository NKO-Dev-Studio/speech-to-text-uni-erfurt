package studio.nkodev.stt.adapter.grpc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.storage.audio.SharedStorageAudioFileProviderFactory;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SpeechToTextGrpcServerTest {

  private static SpeechToTextGrpcServerConfiguration configuration;

  @BeforeAll
  static void setUp() {
    configuration =
        new SpeechToTextGrpcServerConfiguration() {
          @Override
          public int getPort() {
            return 0;
          }

          @Override
          public Path getServerCertificatePath() {
            return Path.of(
                SpeechToTextGrpcServerTest.class
                    .getResource("speech-to-text-service.cert.pem")
                    .getPath());
          }

          @Override
          public Path getServerPrivateKeyPath() {
            return Path.of(
                SpeechToTextGrpcServerTest.class
                    .getResource("speech-to-text-service.key.pem")
                    .getPath());
          }

          @Override
          public Optional<Path> getServerPrivateKeyPasswordPath() {
            return Optional.empty();
          }
        };
  }

  @Test
  void shouldStartGrpcServerSuccessfully() {
    SpeechToTextGrpcServer grpcServer =
        new SpeechToTextGrpcServer(
            configuration, null, null, new SharedStorageAudioFileProviderFactory());

    assertDoesNotThrow(grpcServer::start, "Successfully started grpc server");
    assertDoesNotThrow(grpcServer::stop, "Successfully stopped grpc server");
  }
}
