package studio.nkodev.stt.service.exception;

/**
 * This exception will be thrown when a feature is disabled because of the current active
 * configuration.
 *
 * <p><strong>Example:</strong> No configuration of a shared result storage provided => The action
 * to move a result into a shared storage is disabled
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.03.26
 */
public class FeatureDisabledException extends RuntimeException {

  public FeatureDisabledException() {}

  public FeatureDisabledException(String message) {
    super(message);
  }

  public FeatureDisabledException(String message, Throwable cause) {
    super(message, cause);
  }

  public FeatureDisabledException(Throwable cause) {
    super(cause);
  }

  public FeatureDisabledException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
