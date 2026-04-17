package studio.nkodev.stt.engine.openai.client;

/**
 * Indicates an error while communicating with the OpenAI compatible API.
 *
 * @author Nico Kotlenga
 * @since 17.04.26
 */
public class OpenAIApiClientException extends Exception {

  public OpenAIApiClientException(String message) {
    super(message);
  }

  public OpenAIApiClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
