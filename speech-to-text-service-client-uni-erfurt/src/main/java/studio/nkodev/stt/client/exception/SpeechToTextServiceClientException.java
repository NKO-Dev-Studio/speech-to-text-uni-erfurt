package studio.nkodev.stt.client.exception;

/**
 * Exception thrown when the speech-to-text-service client fails to execute a request.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public class SpeechToTextServiceClientException extends RuntimeException {

  public SpeechToTextServiceClientException(String message) {
    super(message);
  }

  public SpeechToTextServiceClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
