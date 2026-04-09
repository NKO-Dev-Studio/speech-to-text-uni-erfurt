package studio.nkodev.stt.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineExecutionException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineMissingResultException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineModelNotFoundException;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.exception.StorageException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testsuite of {@link SpeechToTextTaskExecutor}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
public class SpeechToTextTaskExecutorTest {

  private static final long TASK_ID = 2L;

  private SpeechToTextTask speechToTextTask;
  private SpeechToTextEngine speechToTextEngine;
  private Path audioFile;
  private Path resultFile;
  private SpeechToTextTaskStorage speechToTextTaskStorage;

  private SpeechToTextTaskExecutor speechToTextTaskExecutor;

  @BeforeEach
  public void setUp() {
    SpeechToTextTaskConfiguration speechToTextTaskConfiguration =
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            null,
            "turbo",
            SpeechToTextEngineOutputFormat.JSON);
    speechToTextEngine = mock(SpeechToTextEngine.class);
    audioFile = mock(Path.class);
    resultFile = mock(Path.class);
    speechToTextTask = mock(SpeechToTextTask.class);
    speechToTextTaskStorage = mock(SpeechToTextTaskStorage.class);

    when(speechToTextTask.getId()).thenReturn(TASK_ID);
    when(speechToTextTask.getConfiguration()).thenReturn(speechToTextTaskConfiguration);

    speechToTextTaskExecutor =
        new SpeechToTextTaskExecutor(
            speechToTextTask, speechToTextEngine, audioFile, resultFile, speechToTextTaskStorage);
  }

  @Test
  public void shouldUpdateTaskStateCorrectlyOnSuccessfulExecution() throws StorageException {
    assertDoesNotThrow(() -> speechToTextTaskExecutor.run());

    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.COMPLETED));
  }

  @Test
  public void shouldUpdateTaskStateCorrectlyOnFailedExecution()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException, StorageException {
    doThrow(SpeechToTextEngineExecutionException.class)
        .when(speechToTextEngine)
        .executeSpeechToTextTask(any());

    assertDoesNotThrow(() -> speechToTextTaskExecutor.run());

    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(TASK_ID), eq(SpeechToTextTaskState.FAILED));
  }
}
