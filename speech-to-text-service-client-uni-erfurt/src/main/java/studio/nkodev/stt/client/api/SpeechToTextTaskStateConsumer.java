package studio.nkodev.stt.client.api;

/**
 * Consumer of speech to text task state changes
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
@FunctionalInterface
public interface SpeechToTextTaskStateConsumer {
  /**
   * Will be triggered when a change of a task state has been detected
   *
   * @param taskId for which a state change has been detected
   * @param speechToTextTaskState of the task
   */
  void onTaskStateChanged(long taskId, SpeechToTextTaskState speechToTextTaskState);
}
