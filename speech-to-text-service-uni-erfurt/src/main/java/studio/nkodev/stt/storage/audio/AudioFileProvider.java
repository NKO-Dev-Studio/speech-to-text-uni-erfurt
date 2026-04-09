package studio.nkodev.stt.storage.audio;

import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Provider of an audio file of a {@link SpeechToTextTask}. An audio file can be provided by
 * different transport mechanisms. These mechanisms are implemented as {@link AudioFileProvider}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 16.03.26
 */
public abstract class AudioFileProvider {

  protected final long taskId;

  public AudioFileProvider(long taskId) {
    this.taskId = taskId;
  }

  /**
   * Persists the provided audio file into the passed {@link AudioFileStorage}
   *
   * @param audioFileStorage in which the provided audio file will be persisted in
   */
  public abstract void persistIntoStorage(AudioFileStorage audioFileStorage)
      throws StorageException;

  public long getTaskId() {
    return taskId;
  }
}
