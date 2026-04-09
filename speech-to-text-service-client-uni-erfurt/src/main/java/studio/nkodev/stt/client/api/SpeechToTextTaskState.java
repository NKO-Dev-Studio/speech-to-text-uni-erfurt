package studio.nkodev.stt.client.api;

/**
 * Different states of a speech to text task
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public enum SpeechToTextTaskState {
  /** The task is waiting for an audio file to process */
  WAITING_FOR_AUDIO_FILE,
  /** The task is currently queued and waits for a free execution slot */
  PENDING,
  /** The task is currently executed */
  RUNNING,
  /** The task is finished. Task results are now available */
  DONE,
  /** An error occurred during executing the task */
  FAILED;

  public boolean isTerminal() {
    return this == DONE || this == FAILED;
  }
}
