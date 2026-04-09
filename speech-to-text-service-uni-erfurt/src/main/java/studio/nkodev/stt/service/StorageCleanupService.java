package studio.nkodev.stt.service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage.SpeechToTextTaskFilter;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;

/**
 * This service is responsible to clean storages by deleting task information and results of tasks
 * which are over a certain age and are in a terminal state or waiting for an audio file. <br>
 * Therefor, each hour the task storage is queried to receive task exceeding the configured maximum
 * age and are in state COMPLETED, FAILED or WAITING_FOR_AUDIO_FILE. <br>
 * The results of the founded tasks inside the {@link
 * studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage}, the persisted audio files inside
 * the {@link studio.nkodev.stt.storage.audio.AudioFileStorage } as well as the entries inside the
 * {@link studio.nkodev.stt.storage.SpeechToTextTaskStorage} gets deleted.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class StorageCleanupService {
  private static final Logger logger = LoggerFactory.getLogger(StorageCleanupService.class);

  private static final Collection<SpeechToTextTaskState> TASK_STATES =
      Set.of(SpeechToTextTaskState.FAILED, SpeechToTextTaskState.COMPLETED, SpeechToTextTaskState.WAITING_FOR_AUDIO);
  private static final long MAX_CHANGED_AT_AGE_IN_MILLIS = 2 * 60 * 60 * 1000;

  private final SpeechToTextTaskResultStorage speechToTextTaskResultStorage;
  private final AudioFileStorage audioFileStorage;
  private final SpeechToTextTaskStorage speechToTextTaskStorage;

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread t1 = new Thread(runnable, "storage-cleanup");
            t1.setDaemon(true);
            return t1;
          });

  public StorageCleanupService(
      SpeechToTextTaskStorage speechToTextTaskStorage,
      AudioFileStorage audioFileStorage,
      SpeechToTextTaskResultStorage resultStorage) {
    this.speechToTextTaskStorage = speechToTextTaskStorage;
    this.audioFileStorage = audioFileStorage;
    this.speechToTextTaskResultStorage = resultStorage;

    this.scheduledExecutorService.scheduleWithFixedDelay(
        this::executeCleanUp, 0, 1, TimeUnit.HOURS);
  }

  private void executeCleanUp() {
    logger.info(
        "Start deletion of terminal tasks which last change are older than {} minutes ",
        MAX_CHANGED_AT_AGE_IN_MILLIS / 1000 / 60);

    long changedAtBeforeMs = System.currentTimeMillis() - MAX_CHANGED_AT_AGE_IN_MILLIS;
    SpeechToTextTaskFilter filter =
        SpeechToTextTaskFilter.builder()
            .lastChangeBeforeTimestampMillis(changedAtBeforeMs)
            .states(TASK_STATES)
            .build();

    try {
      speechToTextTaskStorage
          .getTasks(filter)
          .forEach(
              task -> {
                logger.debug("Deleting all data of task {}", task.getId());

                try {
                  audioFileStorage.deleteAudioFileFromStorage(task.getId());
                  speechToTextTaskResultStorage.removeResultsOfTask(task.getId());
                  speechToTextTaskStorage.deleteTask(task.getId());
                } catch (Exception e) {
                  logger.error(
                      "Error during deleting information of task with id {}", task.getId(), e);
                }
              });
    } catch (Exception e) {
      logger.error("Error during executing cleanup task. Reason: ", e);
    }
  }

  public void close() {
    scheduledExecutorService.shutdown();
  }
}
