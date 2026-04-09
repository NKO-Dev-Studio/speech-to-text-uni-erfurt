package studio.nkodev.stt.storage.exception;

import java.util.UUID;

/**
 * This exception will be thrown when a storage error occurred
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 08.03.26
 */
public class StorageException extends Exception {
  public StorageException() {
  }

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }

  public StorageException(Throwable cause) {
    super(cause);
  }

  public StorageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
