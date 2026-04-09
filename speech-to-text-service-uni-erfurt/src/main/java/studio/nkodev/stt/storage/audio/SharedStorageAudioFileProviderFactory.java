package studio.nkodev.stt.storage.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.service.exception.FeatureDisabledException;

/**
 * Factory creating the correct {@link AudioFileProvider} based on input type of the audio file
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public class SharedStorageAudioFileProviderFactory {

  private static final Logger logger =
      LoggerFactory.getLogger(SharedStorageAudioFileProviderFactory.class);

  private SharedAudioFileStorage sharedAudioFileStorage;

  public AudioFileProvider createProviderForSharedStorageFile(long taskId, String fileName) {
    logger.trace("Creating provider for shared storage audio file for task {}", taskId);

    if (sharedAudioFileStorage == null) {
      throw new FeatureDisabledException("No shared audio storage provided");
    }

    return new SharedStorageAudioFileProvider(sharedAudioFileStorage, taskId, fileName);
  }

  public void setSharedAudioFileStorage(SharedAudioFileStorage sharedAudioFileStorage) {
    this.sharedAudioFileStorage = sharedAudioFileStorage;
  }
}
