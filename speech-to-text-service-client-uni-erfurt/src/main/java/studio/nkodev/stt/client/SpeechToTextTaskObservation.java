package studio.nkodev.stt.client;

import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.api.SpeechToTextTaskStateConsumer;

/**
 * State holder of a running task observation.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
final class SpeechToTextTaskObservation {

  private final long taskId;
  private final SpeechToTextTaskStateConsumer taskStateConsumer;
  private SpeechToTextTaskState lastObservedState;
  private long currentDelayMs;
  private boolean active = true;

  SpeechToTextTaskObservation(
      long taskId, SpeechToTextTaskStateConsumer taskStateConsumer, long initialDelayMs) {
    this.taskId = taskId;
    this.taskStateConsumer = taskStateConsumer;
    this.currentDelayMs = initialDelayMs;
  }

  long getTaskId() {
    return taskId;
  }

  SpeechToTextTaskStateConsumer getTaskStateConsumer() {
    return taskStateConsumer;
  }

  synchronized boolean registerState(SpeechToTextTaskState currentTaskState) {
    boolean changed = !currentTaskState.equals(lastObservedState);
    lastObservedState = currentTaskState;
    return changed;
  }

  synchronized long increaseDelay(double multiplier, long maximumDelayMs) {
    currentDelayMs = Math.min(maximumDelayMs, Math.max(currentDelayMs, 1L));
    currentDelayMs = Math.min(maximumDelayMs, Math.round(currentDelayMs * multiplier));
    return currentDelayMs;
  }

  synchronized boolean isActive() {
    return active;
  }

  synchronized void stop() {
    active = false;
  }
}
