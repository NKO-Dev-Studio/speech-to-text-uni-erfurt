package studio.nkodev.stt.engine.exception;

import studio.nkodev.stt.engine.api.SpeechToTextEngineModel;

/**
 * This exception will be thrown when the engine execution failed.
 *
 * @author Nico Kotlenga
 * @since 26.12.2026
 */
public class SpeechToTextEngineExecutionException extends SpeechToTextEngineException {
  public SpeechToTextEngineExecutionException(String message) {
    super(message);
  }

  public SpeechToTextEngineExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
