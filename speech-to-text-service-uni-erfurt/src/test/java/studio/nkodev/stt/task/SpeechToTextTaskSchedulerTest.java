package studio.nkodev.stt.task;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.SpeechToTextEngineRegistry;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;
import studio.nkodev.stt.task.api.SpeechToTextTaskSchedulerConfiguration;

/**
 * Testsuite of {@link SpeechToTextTaskScheduler}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
public class SpeechToTextTaskSchedulerTest {

  private static final long TASK_ID = 2L;
  private static final int WAIT_TIMEOUT_SECONDS = 2;

  private SpeechToTextEngineRegistry speechToTextEngineRegistry;
  private SpeechToTextTaskStorage speechToTextTaskStorage;
  private SpeechToTextTaskResultStorage speechToTextTaskResultStorage;
  private AudioFileStorage audioFileStorage;
  private SpeechToTextEngine speechToTextEngine;
  private Path audioFilePath;
  private Path resultDirectory;
  private SpeechToTextTaskScheduler speechToTextTaskScheduler;

  @BeforeEach
  void setUp() throws Exception {
    speechToTextEngineRegistry = mock(SpeechToTextEngineRegistry.class);
    speechToTextTaskStorage = mock(SpeechToTextTaskStorage.class);
    speechToTextTaskResultStorage = mock(SpeechToTextTaskResultStorage.class);
    audioFileStorage = mock(AudioFileStorage.class);
    speechToTextEngine = mock(SpeechToTextEngine.class);
    audioFilePath = mock(Path.class);
    resultDirectory = mock(Path.class);

    when(speechToTextTaskStorage.getTasks(any())).thenReturn(Stream.empty());
    when(speechToTextTaskStorage.claimNextPendingTask()).thenReturn(Optional.empty());
    when(
            speechToTextEngineRegistry.getEngineByIdentifier(
                eq(SpeechToTextEngineType.WHISPER_LOCAL.name())))
        .thenReturn(speechToTextEngine);
    when(audioFileStorage.getAudioFilePathOfTask(eq(TASK_ID))).thenReturn(audioFilePath);
    when(speechToTextTaskResultStorage.createTaskResultDirectory(eq(TASK_ID)))
        .thenReturn(resultDirectory);
  }

  @AfterEach
  void tearDown() {
    if (speechToTextTaskScheduler != null) {
      speechToTextTaskScheduler.shutdown();
    }
  }

  @Test
  void shouldExecuteClaimedPendingTaskSuccessfully() throws Exception {
    SpeechToTextTask pendingTask = createTask(TASK_ID);
    CountDownLatch completionLatch = new CountDownLatch(1);

    when(speechToTextTaskStorage.claimNextPendingTask())
        .thenReturn(Optional.of(pendingTask), Optional.empty());
    doAnswer(
            invocation -> {
              completionLatch.countDown();
              return null;
            })
        .when(speechToTextTaskStorage)
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.COMPLETED));

    speechToTextTaskScheduler = createScheduler();

    await(completionLatch);

    verify(speechToTextEngine, times(1)).executeSpeechToTextTask(any());
    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.COMPLETED));
  }

  @Test
  void shouldRestoreRunningTasksOnStartup() throws Exception {
    SpeechToTextTask runningTask = createTask(TASK_ID);
    CountDownLatch completionLatch = new CountDownLatch(1);
    ArgumentCaptor<SpeechToTextTaskStorage.SpeechToTextTaskFilter> filterCaptor =
        ArgumentCaptor.forClass(SpeechToTextTaskStorage.SpeechToTextTaskFilter.class);

    when(speechToTextTaskStorage.getTasks(any())).thenReturn(Stream.of(runningTask));
    doAnswer(
            invocation -> {
              completionLatch.countDown();
              return null;
            })
        .when(speechToTextTaskStorage)
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.COMPLETED));

    speechToTextTaskScheduler = createScheduler();

    await(completionLatch);

    verify(speechToTextTaskStorage, times(1)).getTasks(filterCaptor.capture());
    assertTrue(
        Collections.singleton(SpeechToTextTaskState.RUNNING)
            .equals(filterCaptor.getValue().states()));
    verify(speechToTextEngine, times(1)).executeSpeechToTextTask(any());
    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.COMPLETED));
  }

  @Test
  void shouldMarkTaskAsFailedWhenTaskPreparationFails() throws Exception {
    SpeechToTextTask pendingTask = createTask(TASK_ID);
    CountDownLatch failureLatch = new CountDownLatch(1);

    when(speechToTextTaskStorage.claimNextPendingTask())
        .thenReturn(Optional.of(pendingTask), Optional.empty());
    when(audioFileStorage.getAudioFilePathOfTask(eq(TASK_ID)))
        .thenThrow(new NotFoundStorageException("speech-to-text-task-audio-file", TASK_ID));
    doAnswer(
            invocation -> {
              failureLatch.countDown();
              return null;
            })
        .when(speechToTextTaskStorage)
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.FAILED));

    speechToTextTaskScheduler = createScheduler();

    await(failureLatch);

    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.FAILED));
    verify(speechToTextEngine, never()).executeSpeechToTextTask(any());
  }

  @Test
  void shouldThrowExceptionWhenLoadingRunningTasksFails() throws Exception {
    when(speechToTextTaskStorage.getTasks(any()))
        .thenThrow(new StorageException("Failed to load running tasks"));

    assertThrows(IllegalStateException.class, this::createScheduler);
  }

  private SpeechToTextTaskScheduler createScheduler() {
    SpeechToTextTaskSchedulerConfiguration configuration = () -> 1;
    return new SpeechToTextTaskScheduler(
        configuration,
        speechToTextEngineRegistry,
        speechToTextTaskStorage,
        speechToTextTaskResultStorage,
        audioFileStorage);
  }

  private SpeechToTextTask createTask(long taskId) {
    SpeechToTextTaskConfiguration taskConfiguration =
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            null,
            "turbo",
            SpeechToTextEngineOutputFormat.JSON);
    Instant now = Instant.now();
    return new SpeechToTextTask(taskId, taskConfiguration, now, now);
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }
}
