package studio.nkodev.stt.service;

import java.nio.file.Path;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.SpeechToTextEngineRegistry;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.service.exception.FeatureDisabledException;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.audio.AudioFileChunk;
import studio.nkodev.stt.storage.audio.AudioFileChunkConsumer;
import studio.nkodev.stt.storage.audio.AudioFileProvider;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultConsumer;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;
import studio.nkodev.stt.storage.result.SpeechToTextTaskSharedResultStorage;

/**
 * This service provides all functions to create and receive information of speech to text tasks
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public class SpeechToTextService {
  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextService.class);

  private final SpeechToTextTaskStorage speechToTextTaskStorage;
  private final SpeechToTextTaskResultStorage speechToTextTaskResultStorage;
  private final AudioFileStorage audioFileStorage;
  private final SpeechToTextEngineRegistry speechToTextEngineRegistry;

  /** Optional defined shared result storage */
  private SpeechToTextTaskSharedResultStorage sharedResultStorage;

  public SpeechToTextService(
      SpeechToTextTaskStorage speechToTextTaskStorage,
      SpeechToTextTaskResultStorage speechToTextTaskResultStorage,
      AudioFileStorage audioFileStorages,
      SpeechToTextEngineRegistry speechToTextEngineRegistry) {
    this.speechToTextTaskStorage = speechToTextTaskStorage;
    this.speechToTextTaskResultStorage = speechToTextTaskResultStorage;
    this.audioFileStorage = audioFileStorages;
    this.speechToTextEngineRegistry = speechToTextEngineRegistry;
  }

  public long createSpeechToTextTask(SpeechToTextTaskConfiguration speechToTextTaskConfiguration)
      throws StorageException {
    logger.debug(
        "Creating new speech-to-text-task having configuration: {}", speechToTextTaskConfiguration);

    try {
      SpeechToTextTask speechToTextTask =
          speechToTextTaskStorage.createTask(speechToTextTaskConfiguration);
      return speechToTextTask.getId();
    } catch (Exception e) {
      logger.error("Error during creating speech to text task. Reason:", e);
      throw e;
    }
  }

  public void provideAudioFile(AudioFileProvider audioFileProvider) throws StorageException {
    logger.debug("Providing audio file of task having id: {}", audioFileProvider.getTaskId());

    try {
      SpeechToTextTask speechToTextTask =
          speechToTextTaskStorage.getTask(audioFileProvider.getTaskId());
      verifyTaskState(speechToTextTask, SpeechToTextTaskState.WAITING_FOR_AUDIO);
      audioFileStorage.provideAudioFile(audioFileProvider);
      markTaskPendingForExecution(speechToTextTask.getId());
    } catch (Exception exception) {
      logger.error(
          "Error during providing audio file of task having id: {}",
          audioFileProvider.getTaskId(),
          exception);
      throw exception;
    }
  }

  public AudioFileChunkConsumer createAudioFileChunkConsumer(long taskId) throws StorageException {
    logger.debug("Creating audio file chunk consumer of task having id: {}", taskId);

    try {
      SpeechToTextTask speechToTextTask = speechToTextTaskStorage.getTask(taskId);
      verifyTaskState(speechToTextTask, SpeechToTextTaskState.WAITING_FOR_AUDIO);

      AudioFileChunkConsumer delegatedAudioFileChunkConsumer =
          audioFileStorage.createAudioFileChunkConsumer(taskId);
      return new TaskAwareAudioFileChunkConsumer(taskId, delegatedAudioFileChunkConsumer);
    } catch (Exception exception) {
      logger.error(
          "Error during creating audio chunk consumer of task having id: {}", taskId, exception);
      throw exception;
    }
  }

  private void markTaskPendingForExecution(long taskId) throws StorageException {
    speechToTextTaskStorage.updateTaskState(taskId, SpeechToTextTaskState.PENDING);
  }

  /**
   * Loads the current state of the specified tasks
   *
   * @param speechToTextTaskId of the task of which the {@link SpeechToTextTaskState} loads from
   * @return the {@link SpeechToTextTaskState}
   * @throws StorageException when an error occurred during loading the task state
   * @throws NotFoundStorageException when the specified task can't be found
   */
  public SpeechToTextTaskState getSpeechToTextTaskState(long speechToTextTaskId)
      throws StorageException {
    logger.debug("Loading task state of task having id: {}", speechToTextTaskId);
    try {
      return speechToTextTaskStorage.getTaskState(speechToTextTaskId);
    } catch (Exception exception) {
      logger.error(
          "Error during loading task state of task having id: {}", speechToTextTaskId, exception);
      throw exception;
    }
  }

  public Path moveResultsOfTasksToSharedStorage(long taskId) throws StorageException {
    if (sharedResultStorage == null) {
      throw new FeatureDisabledException(
          "Feature allowing to move task results to shared storage is currently disabled. No shared storage provided.");
    }

    try {
      SpeechToTextTask speechToTextTask = speechToTextTaskStorage.getTask(taskId);
      verifyTaskState(speechToTextTask, SpeechToTextTaskState.COMPLETED);

      try (SpeechToTextTaskResultConsumer resultConsumer =
          speechToTextTaskResultStorage.createTaskResultConsumer(taskId)) {
        return sharedResultStorage.persistResultIntoSharedStorage(resultConsumer);
      }
    } catch (Exception exception) {
      logger.error(
          "Error during moving task results of task having id {} to shared storage",
          taskId,
          exception);
      throw exception;
    }
  }

  public SpeechToTextTaskResultConsumer consumeSpeechToTextTaskResults(long taskId)
      throws StorageException {
    try {
      SpeechToTextTask speechToTextTask = speechToTextTaskStorage.getTask(taskId);
      verifyTaskState(speechToTextTask, SpeechToTextTaskState.COMPLETED);

      return speechToTextTaskResultStorage.createTaskResultConsumer(taskId);
    } catch (Exception exception) {
      logger.error("Error during getting task result of task having id: {}", taskId, exception);
      throw exception;
    }
  }

  public Collection<SpeechToTextEngine> getSpeechToTextEngines() {
    return speechToTextEngineRegistry.getEngines();
  }

  public void setSharedResultStorage(SpeechToTextTaskSharedResultStorage sharedResultStorage) {
    this.sharedResultStorage = sharedResultStorage;
  }

  private void verifyTaskState(
      SpeechToTextTask speechToTextTask, SpeechToTextTaskState expectedTaskState)
      throws StorageException {
    SpeechToTextTaskState currentSpeechToTextTaskState =
        speechToTextTaskStorage.getTaskState(speechToTextTask.getId());
    if (!currentSpeechToTextTaskState.equals(expectedTaskState)) {
      throw new IllegalStateException(
          "Task has an unexpected state. Expected:"
              + expectedTaskState
              + ", actual:"
              + currentSpeechToTextTaskState);
    }
  }

  private final class TaskAwareAudioFileChunkConsumer implements AudioFileChunkConsumer {

    private final long taskId;
    private final AudioFileChunkConsumer delegatedAudioFileChunkConsumer;
    private boolean completed;

    private TaskAwareAudioFileChunkConsumer(
        long taskId, AudioFileChunkConsumer delegatedAudioFileChunkConsumer) {
      this.taskId = taskId;
      this.delegatedAudioFileChunkConsumer = delegatedAudioFileChunkConsumer;
    }

    @Override
    public void consume(AudioFileChunk audioFileChunk) throws StorageException {
      delegatedAudioFileChunkConsumer.consume(audioFileChunk);

      if (audioFileChunk.lastChunk() && !completed) {
        completed = true;
      }
    }

    @Override
    public void close() throws StorageException {
      try {
        delegatedAudioFileChunkConsumer.close();
      } finally {
        SpeechToTextTaskState speechToTextTaskState =
            completed ? SpeechToTextTaskState.PENDING : SpeechToTextTaskState.FAILED;

        try {
          speechToTextTaskStorage.updateTaskState(taskId, speechToTextTaskState);
        } catch (Exception ex) {
          logger.error("Error during updating task state of task having id: {}", taskId, ex);
        }
      }
    }
  }
}
