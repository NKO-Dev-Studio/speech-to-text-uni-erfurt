package studio.nkodev.stt.storage;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * The {@link SpeechToTextTaskStorage} contains the information about all currently defined {@link
 * SpeechToTextTask}s.
 *
 * <p>It provides methods to receive tasks and change their state.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public interface SpeechToTextTaskStorage {
  /**
   * @param taskId
   * @return the task having the specified id
   */
  SpeechToTextTask getTask(long taskId) throws StorageException;

  SpeechToTextTaskState getTaskState(long taskId) throws StorageException;

  /**
   * @return all currently defined tasks
   */
  Stream<SpeechToTextTask> getTasks() throws StorageException;

  /**
   * @param filter which the returned tasks must match
   * @return all tasks matching the specified filter criteria
   */
  Stream<SpeechToTextTask> getTasks(SpeechToTextTaskFilter filter) throws StorageException;

  /**
   * Loads the next task waiting for execution and atomically marks it as running.
   *
   * @return the claimed task if one is available
   */
  Optional<SpeechToTextTask> claimNextPendingTask() throws StorageException;

  /**
   * Creates a new task
   *
   * @param taskConfiguration which the new created task will have
   * @return the created {@link SpeechToTextTask}
   */
  SpeechToTextTask createTask(SpeechToTextTaskConfiguration taskConfiguration)
      throws StorageException;

  /**
   * Updates the state of the passed {@link SpeechToTextTask}
   *
   * @param taskId
   * @param taskState
   */
  void updateTaskState(long taskId, SpeechToTextTaskState taskState) throws StorageException;

  /**
   * Deletes the specified task
   * @param taskId of the task which will be deleted
   * @throws StorageException will be thrown when an error occurred during task deletion
   */
  void deleteTask(long taskId) throws StorageException;

  /** Filter which can be applied to receive speech to text tasks */
  record SpeechToTextTaskFilter(
      Collection<SpeechToTextTaskState> states, Long lastChangeBeforeTimestampMillis) {

    public static SpeechToTextTaskFilterBuilder builder() {
      return new SpeechToTextTaskFilterBuilder();
    }

    public static class SpeechToTextTaskFilterBuilder {
      private Collection<SpeechToTextTaskState> states = null;
      private Long lastChangeBeforeTimestampMillis = null;

      public SpeechToTextTaskFilterBuilder states(Collection<SpeechToTextTaskState> states) {
        this.states = states;
        return this;
      }

      public SpeechToTextTaskFilterBuilder lastChangeBeforeTimestampMillis(
          long lastChangeBeforeTimestampMillis) {
        this.lastChangeBeforeTimestampMillis = lastChangeBeforeTimestampMillis;
        return this;
      }

      public SpeechToTextTaskFilter build() {
        return new SpeechToTextTaskFilter(states, lastChangeBeforeTimestampMillis);
      }
    }
  }
}
