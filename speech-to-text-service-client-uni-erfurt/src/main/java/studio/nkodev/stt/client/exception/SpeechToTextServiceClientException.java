package studio.nkodev.stt.client.exception;

/**
 * Exception thrown when the speech-to-text-service client fails to execute a request.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public class SpeechToTextServiceClientException extends RuntimeException {

  private final SpeechToTextServiceClientErrorType errorType;

  public SpeechToTextServiceClientException(String message) {
    this(SpeechToTextServiceClientErrorType.INTERNAL_SERVER_ERROR, message);
  }

  public SpeechToTextServiceClientException(String message, Throwable cause) {
    this(SpeechToTextServiceClientErrorType.INTERNAL_SERVER_ERROR, message, cause);
  }

  public SpeechToTextServiceClientException(
      SpeechToTextServiceClientErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public SpeechToTextServiceClientException(
      SpeechToTextServiceClientErrorType errorType, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
  }

  /**
   * @return the type of error that caused this exception
   */
  public SpeechToTextServiceClientErrorType getErrorType() {
    return errorType;
  }
}
