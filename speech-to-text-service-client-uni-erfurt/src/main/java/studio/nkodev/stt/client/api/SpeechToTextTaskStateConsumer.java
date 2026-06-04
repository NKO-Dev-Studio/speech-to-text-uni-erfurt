package studio.nkodev.stt.client.api;

import studio.nkodev.stt.client.exception.SpeechToTextServiceClientErrorType;

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

  /**
   * Will be triggered when an error occurs while observing a task.
   *
   * @param taskId for which the error occurred
   * @param errorType describing the kind of error
   * @param terminal {@code true} if the observation has been stopped because of this error,
   *     {@code false} if the observation continues
   */
  default void onObservationError(
      long taskId, SpeechToTextServiceClientErrorType errorType, boolean terminal) {}
}
