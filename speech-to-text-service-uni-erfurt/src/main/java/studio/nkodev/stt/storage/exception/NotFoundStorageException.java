package studio.nkodev.stt.storage.exception;

import java.text.MessageFormat;
import java.util.UUID;

/**
 * This exception will be thrown when the specified result can't been found inside the result
 * storage
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.03.26
 */
public class NotFoundStorageException extends StorageException {

  private final String entityTypeDescription;
  private final Object entityId;

  public NotFoundStorageException(String entityTypeDescription, Object entityId) {
    super(
        MessageFormat.format("No {0} entry found having id {1}.", entityTypeDescription, entityId));
    this.entityTypeDescription = entityTypeDescription;
    this.entityId = entityId;
  }

  public String getEntityTypeDescription() {
    return entityTypeDescription;
  }

  public Object getEntityId() {
    return entityId;
  }
}
