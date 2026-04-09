package studio.nkodev.stt.adapter.grpc;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Configurations of the {@link SpeechToTextGrpcServer}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 14.03.26
 */
public interface SpeechToTextGrpcServerConfiguration {
  int getPort();

  Path getServerCertificatePath();

  Path getServerPrivateKeyPath();

  Optional<Path> getServerPrivateKeyPasswordPath();
}
