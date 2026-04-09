package studio.nkodev.stt.engine.exception;

/**
 * Base exception type used by a {@link studio.nkodev.stt.engine.api.SpeechToTextEngine} to signalize errors
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class SpeechToTextEngineException extends Exception{

  public SpeechToTextEngineException() {
  }

  public SpeechToTextEngineException(String message) {
    super(message);
  }

  public SpeechToTextEngineException(String message, Throwable cause) {
    super(message, cause);
  }

  public SpeechToTextEngineException(Throwable cause) {
    super(cause);
  }

  public SpeechToTextEngineException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
