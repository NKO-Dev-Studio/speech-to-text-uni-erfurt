package studio.nkodev.stt.auth;

/**
 * Thrown when a request cannot be authenticated.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public class AuthenticationException extends Exception {

  public AuthenticationException(String message) {
    super(message);
  }

  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
