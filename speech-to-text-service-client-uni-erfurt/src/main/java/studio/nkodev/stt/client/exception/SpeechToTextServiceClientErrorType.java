package studio.nkodev.stt.client.exception;

/**
 * Categorizes failures reported by the speech-to-text-service client.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 04.06.26
 */
public enum SpeechToTextServiceClientErrorType {
  /** The requested resource (e.g. a task) does not exist on the server */
  NOT_FOUND,
  /** The server failed to process the request */
  INTERNAL_SERVER_ERROR,
  /** The server could not be reached */
  CONNECTION_ERROR
}
