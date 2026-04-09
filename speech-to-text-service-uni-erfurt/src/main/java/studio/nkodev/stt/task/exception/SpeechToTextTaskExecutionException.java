package studio.nkodev.stt.task.exception;

import studio.nkodev.stt.api.SpeechToTextTask;

import java.text.MessageFormat;
import java.util.UUID;

/**
 * This execption will be thrown when an error occurred during executing a {@link
 * SpeechToTextTask}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.03.26
 */
public class SpeechToTextTaskExecutionException extends Exception {

  private final UUID taskId;

  public SpeechToTextTaskExecutionException(UUID taskId) {
    this(taskId, null);
  }

  public SpeechToTextTaskExecutionException(UUID taskId, Exception cause) {
    super(MessageFormat.format("Error during executing task having id {0}", taskId), cause);
    this.taskId = taskId;
  }

  public UUID getTaskId() {
    return taskId;
  }
}
