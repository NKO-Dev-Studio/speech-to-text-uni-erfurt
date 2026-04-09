package studio.nkodev.stt.api;

import java.util.Collections;
import java.util.Set;

/**
 * Describes the different states a speech to text task can have
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public enum SpeechToTextTaskState {
  /** The task is waiting for audio */
  WAITING_FOR_AUDIO(Collections.emptySet()),
  /** The task is waiting for its execution */
  PENDING(Set.of(WAITING_FOR_AUDIO)),
  /** The task is currently executing */
  RUNNING(Set.of(PENDING)),
  /** The task has been completed successfully */
  COMPLETED(Set.of(RUNNING)),
  /** The task has been failed */
  FAILED(Set.of(WAITING_FOR_AUDIO, PENDING, RUNNING));

  private final Set<SpeechToTextTaskState> allowedPreviousSpeechToTextTaskStates;

  SpeechToTextTaskState(Set<SpeechToTextTaskState> allowedPreviousSpeechToTextTaskStates) {
    this.allowedPreviousSpeechToTextTaskStates = allowedPreviousSpeechToTextTaskStates;
  }

  public Set<SpeechToTextTaskState> getAllowedPreviousSpeechToTextTaskStates() {
    return allowedPreviousSpeechToTextTaskStates;
  }
}
