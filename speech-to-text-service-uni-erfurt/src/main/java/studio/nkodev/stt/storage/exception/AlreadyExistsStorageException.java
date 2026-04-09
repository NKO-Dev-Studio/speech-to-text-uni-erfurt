package studio.nkodev.stt.storage.exception;

import java.text.MessageFormat;
import java.util.UUID;

/**
 * This exception will be thrown when trying to create a result directory for a task
 * which already exists
 */
public class AlreadyExistsStorageException extends StorageException {

  private final long taskId;

  public AlreadyExistsStorageException(long taskId) {
    super(MessageFormat.format("Entry already exists for task having id {0}", taskId));
    this.taskId = taskId;
  }

  public long getTaskId() {
    return taskId;
  }
}
