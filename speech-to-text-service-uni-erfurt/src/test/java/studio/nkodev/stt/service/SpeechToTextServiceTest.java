package studio.nkodev.stt.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Context.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.SpeechToTextEngineRegistry;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.proto.SpeechToTextService.SharedStorageResult;
import studio.nkodev.stt.service.exception.FeatureDisabledException;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.audio.AudioFileChunk;
import studio.nkodev.stt.storage.audio.AudioFileChunkConsumer;
import studio.nkodev.stt.storage.audio.AudioFileProvider;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.exception.StorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultConsumer;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;
import studio.nkodev.stt.storage.result.SpeechToTextTaskSharedResultStorage;

import java.nio.file.Path;

/**
 * Testsuite of {@link SpeechToTextService}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.04.26
 */
public class SpeechToTextServiceTest {

  private SpeechToTextTaskStorage speechToTextTaskStorage;
  private SpeechToTextTaskResultStorage speechToTextTaskResultStorage;
  private AudioFileStorage audioFileStorage;
  private SpeechToTextEngineRegistry speechToTextEngineRegistry;
  private SpeechToTextService speechToTextService;

  @BeforeEach
  void setUp() {
    speechToTextTaskStorage = mock(SpeechToTextTaskStorage.class);
    speechToTextTaskResultStorage = mock(SpeechToTextTaskResultStorage.class);
    audioFileStorage = mock(AudioFileStorage.class);
    speechToTextEngineRegistry = mock(SpeechToTextEngineRegistry.class);

    speechToTextService =
        new SpeechToTextService(
            speechToTextTaskStorage,
            speechToTextTaskResultStorage,
            audioFileStorage,
            speechToTextEngineRegistry);
  }

  @Test
  public void createSpeechToTextTaskSuccessfully() throws StorageException {
    SpeechToTextTaskConfiguration taskConfiguration =
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            null,
            "turbo",
            SpeechToTextEngineOutputFormat.JSON);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.createTask(eq(taskConfiguration))).thenReturn(speechToTextTask);

    long createdSpeechToTextTaskId = speechToTextService.createSpeechToTextTask(taskConfiguration);
    assertEquals(2L, createdSpeechToTextTaskId);
  }

  @Test
  public void createSpeechToTextTaskShouldThrowException() throws StorageException {
    SpeechToTextTaskConfiguration taskConfiguration =
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            null,
            "turbo",
            SpeechToTextEngineOutputFormat.JSON);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.createTask(eq(taskConfiguration)))
        .thenThrow(new StorageException("Error during creating task"));

    assertThrows(
        StorageException.class,
        () -> speechToTextService.createSpeechToTextTask(taskConfiguration));
  }

  @Test
  public void providingAudioFileSuccessfully() throws StorageException {
    AudioFileProvider audioFileProvider = mock(AudioFileProvider.class);
    when(audioFileProvider.getTaskId()).thenReturn(2L);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L)))
        .thenReturn(SpeechToTextTaskState.WAITING_FOR_AUDIO);
    when(speechToTextTaskStorage.getTask(eq(2L))).thenReturn(speechToTextTask);

    assertDoesNotThrow(() -> speechToTextService.provideAudioFile(audioFileProvider));

    verify(audioFileStorage, times(1)).provideAudioFile(eq(audioFileProvider));
    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(2L), eq(SpeechToTextTaskState.PENDING));
  }

  @Test
  public void providingAudioFileShouldFailBecauseOfWrongTaskState() throws StorageException {
    AudioFileProvider audioFileProvider = mock(AudioFileProvider.class);
    when(audioFileProvider.getTaskId()).thenReturn(2L);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.PENDING);
    when(speechToTextTaskStorage.getTask(eq(2L))).thenReturn(speechToTextTask);

    assertThrows(
        IllegalStateException.class, () -> speechToTextService.provideAudioFile(audioFileProvider));
    verify(audioFileStorage, never()).provideAudioFile(eq(audioFileProvider));
    verify(speechToTextTaskStorage, never())
        .updateTaskState(eq(2L), eq(SpeechToTextTaskState.PENDING));
  }

  @Test
  public void providingAudioFileShouldFailBecauseOfStorageException() throws StorageException {
    AudioFileProvider audioFileProvider = mock(AudioFileProvider.class);
    when(audioFileProvider.getTaskId()).thenReturn(2L);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L)))
        .thenReturn(SpeechToTextTaskState.WAITING_FOR_AUDIO);
    when(speechToTextTaskStorage.getTask(eq(2L))).thenReturn(speechToTextTask);
    doThrow(new StorageException("Error during persisting audio file"))
        .when(audioFileStorage)
        .provideAudioFile(eq(audioFileProvider));

    assertThrows(
        StorageException.class, () -> speechToTextService.provideAudioFile(audioFileProvider));

    verify(audioFileStorage, times(1)).provideAudioFile(eq(audioFileProvider));
    verify(speechToTextTaskStorage, never())
        .updateTaskState(eq(2L), eq(SpeechToTextTaskState.PENDING));
  }

  @Test
  public void creatingAudioFileChunkConsumerSuccessfully() throws StorageException {
    long taskId = 2;
    AudioFileChunkConsumer audioFileChunkConsumer = mock(AudioFileChunkConsumer.class);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    when(speechToTextTask.getId()).thenReturn(taskId);
    when(speechToTextTaskStorage.getTaskState(eq(2L)))
        .thenReturn(SpeechToTextTaskState.WAITING_FOR_AUDIO);
    when(speechToTextTaskStorage.getTask(eq(taskId))).thenReturn(speechToTextTask);
    when(audioFileStorage.createAudioFileChunkConsumer(eq(taskId)))
        .thenReturn(audioFileChunkConsumer);

    AudioFileChunkConsumer createdAudioFileChunkConsumer =
        assertDoesNotThrow(() -> speechToTextService.createAudioFileChunkConsumer(taskId));

    AudioFileChunk audioFileChunk = new AudioFileChunk(new byte[0], true);
    createdAudioFileChunkConsumer.consume(audioFileChunk);

    verify(audioFileChunkConsumer, times(1)).consume(eq(audioFileChunk));
    verify(speechToTextTaskStorage, never())
        .updateTaskState(eq(2L), eq(SpeechToTextTaskState.PENDING));

    createdAudioFileChunkConsumer.close();

    verify(speechToTextTaskStorage, times(1))
        .updateTaskState(eq(2L), eq(SpeechToTextTaskState.PENDING));
  }

  @Test
  public void creatingAudioFileChunkConsumerShouldFailBecauseOfWrongTaskState()
      throws StorageException {
    long taskId = 2;
    AudioFileChunkConsumer audioFileChunkConsumer = mock(AudioFileChunkConsumer.class);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    when(speechToTextTask.getId()).thenReturn(taskId);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.FAILED);
    when(speechToTextTaskStorage.getTask(eq(taskId))).thenReturn(speechToTextTask);
    when(audioFileStorage.createAudioFileChunkConsumer(eq(taskId)))
        .thenReturn(audioFileChunkConsumer);

    assertThrows(
        IllegalStateException.class,
        () -> speechToTextService.createAudioFileChunkConsumer(taskId));
    verify(speechToTextTaskStorage, never()).updateTaskState(eq(2L), any());
  }

  @Test
  public void creatingAudioFileChunkConsumerShouldFailBecauseOfStorageException()
      throws StorageException {
    long taskId = 2;
    AudioFileChunkConsumer audioFileChunkConsumer = mock(AudioFileChunkConsumer.class);

    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    when(speechToTextTask.getId()).thenReturn(taskId);
    when(speechToTextTaskStorage.getTaskState(eq(2L)))
        .thenReturn(SpeechToTextTaskState.WAITING_FOR_AUDIO);
    when(speechToTextTaskStorage.getTask(eq(taskId))).thenReturn(speechToTextTask);
    when(audioFileStorage.createAudioFileChunkConsumer(eq(taskId)))
        .thenThrow(new StorageException("Error during creating audio file chunk consumer"));

    assertThrows(
        StorageException.class, () -> speechToTextService.createAudioFileChunkConsumer(taskId));
    verify(speechToTextTaskStorage, never()).updateTaskState(eq(2L), any());
  }

  @Test
  public void shouldReturnTaskStatesSuccessfully() throws StorageException {
    when(speechToTextTaskStorage.getTaskState(eq(1L)))
        .thenReturn(SpeechToTextTaskState.WAITING_FOR_AUDIO);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.PENDING);
    when(speechToTextTaskStorage.getTaskState(eq(3L))).thenReturn(SpeechToTextTaskState.FAILED);
    when(speechToTextTaskStorage.getTaskState(eq(4L))).thenReturn(SpeechToTextTaskState.COMPLETED);

    SpeechToTextTaskState task1 = speechToTextService.getSpeechToTextTaskState(1L);
    SpeechToTextTaskState task2 = speechToTextService.getSpeechToTextTaskState(2L);
    SpeechToTextTaskState task3 = speechToTextService.getSpeechToTextTaskState(3L);
    SpeechToTextTaskState task4 = speechToTextService.getSpeechToTextTaskState(4L);

    assertEquals(SpeechToTextTaskState.WAITING_FOR_AUDIO, task1);
    assertEquals(SpeechToTextTaskState.PENDING, task2);
    assertEquals(SpeechToTextTaskState.FAILED, task3);
    assertEquals(SpeechToTextTaskState.COMPLETED, task4);
  }

  @Test
  public void gettingTaskStateShouldFailBecauseOfStorageException() throws StorageException {
    when(speechToTextTaskStorage.getTaskState(anyLong()))
        .thenThrow(new StorageException("Error during getting task state"));
    assertThrows(StorageException.class, () -> speechToTextService.getSpeechToTextTaskState(1L));
  }

  @Test
  public void movingResultsToSharedStorageSuccessfully() throws StorageException {
    SpeechToTextTaskSharedResultStorage sharedResultStorage =
        mock(SpeechToTextTaskSharedResultStorage.class);
    SpeechToTextTaskResultConsumer resultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    Path resultPath = mock(Path.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.COMPLETED);
    when(speechToTextTaskStorage.getTask(2L)).thenReturn(speechToTextTask);
    when(speechToTextTaskResultStorage.createTaskResultConsumer(2L)).thenReturn(resultConsumer);

    when(sharedResultStorage.persistResultIntoSharedStorage(eq(resultConsumer)))
        .thenReturn(resultPath);

    speechToTextService.setSharedResultStorage(sharedResultStorage);
    Path path = speechToTextService.moveResultsOfTasksToSharedStorage(2L);

    assertEquals(resultPath, path);
  }

  @Test
  public void movingResultsToSharedStorageShouldFailBecauseOfUnconfiguredSharedStorage()
      throws StorageException {
    SpeechToTextTaskSharedResultStorage sharedResultStorage =
        mock(SpeechToTextTaskSharedResultStorage.class);
    SpeechToTextTaskResultConsumer resultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    Path resultPath = mock(Path.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.COMPLETED);
    when(speechToTextTaskStorage.getTask(2L)).thenReturn(speechToTextTask);
    when(speechToTextTaskResultStorage.createTaskResultConsumer(2L)).thenReturn(resultConsumer);

    when(sharedResultStorage.persistResultIntoSharedStorage(eq(resultConsumer)))
        .thenReturn(resultPath);

    assertThrows(
        FeatureDisabledException.class,
        () -> speechToTextService.moveResultsOfTasksToSharedStorage(2L));
  }

  @Test
  public void movingResultsToSharedStorageShouldFailBecauseOfInvalidTaskState()
      throws StorageException {
    SpeechToTextTaskSharedResultStorage sharedResultStorage =
        mock(SpeechToTextTaskSharedResultStorage.class);
    SpeechToTextTaskResultConsumer resultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);
    Path resultPath = mock(Path.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.FAILED);
    when(speechToTextTaskStorage.getTask(2L)).thenReturn(speechToTextTask);
    when(speechToTextTaskResultStorage.createTaskResultConsumer(2L)).thenReturn(resultConsumer);

    when(sharedResultStorage.persistResultIntoSharedStorage(eq(resultConsumer)))
        .thenReturn(resultPath);

    speechToTextService.setSharedResultStorage(sharedResultStorage);
    assertThrows(
        IllegalStateException.class,
        () -> speechToTextService.moveResultsOfTasksToSharedStorage(2L));
  }

  @Test
  public void movingResultsToSharedStorageShouldFailBecauseOfStorageException()
      throws StorageException {
    SpeechToTextTaskSharedResultStorage sharedResultStorage =
        mock(SpeechToTextTaskSharedResultStorage.class);
    SpeechToTextTaskResultConsumer resultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    SpeechToTextTask speechToTextTask = mock(SpeechToTextTask.class);

    when(speechToTextTask.getId()).thenReturn(2L);
    when(speechToTextTaskStorage.getTaskState(eq(2L))).thenReturn(SpeechToTextTaskState.COMPLETED);
    when(speechToTextTaskStorage.getTask(2L)).thenReturn(speechToTextTask);
    when(speechToTextTaskResultStorage.createTaskResultConsumer(2L)).thenReturn(resultConsumer);

    when(sharedResultStorage.persistResultIntoSharedStorage(eq(resultConsumer)))
        .thenThrow(StorageException.class);

    speechToTextService.setSharedResultStorage(sharedResultStorage);
    assertThrows(
        StorageException.class, () -> speechToTextService.moveResultsOfTasksToSharedStorage(2L));
  }
}
