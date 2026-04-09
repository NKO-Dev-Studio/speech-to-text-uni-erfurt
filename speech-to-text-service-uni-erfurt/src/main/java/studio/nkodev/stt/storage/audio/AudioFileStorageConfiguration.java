package studio.nkodev.stt.storage.audio;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Configuration of the {@link AudioFileStorage}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 16.03.26
 */
public interface AudioFileStorageConfiguration {
  Path getAudioFileStorageLocation();
  Optional<Path> getSharedStorageLocation();
}
