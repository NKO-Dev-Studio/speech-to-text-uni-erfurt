package studio.nkodev.stt.adapter.grpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineModel;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.proto.SpeechToTextService;
import studio.nkodev.stt.storage.audio.AudioFileChunk;
import studio.nkodev.stt.storage.audio.AudioFileChunkConsumer;
import studio.nkodev.stt.storage.audio.AudioFileProvider;
import studio.nkodev.stt.storage.audio.SharedStorageAudioFileProviderFactory;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultConsumer;

/**
 * Testsuite of {@link SpeechToTextGrpcService}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class SpeechToTextGrpcServiceTest {

  @TempDir private Path tempDir;

  private studio.nkodev.stt.service.SpeechToTextService speechToTextService;
  private SharedStorageAudioFileProviderFactory sharedStorageAudioFileProviderFactory;
  private SpeechToTextGrpcService grpcService;

  @BeforeEach
  public void setup() {
    speechToTextService = mock(studio.nkodev.stt.service.SpeechToTextService.class);
    sharedStorageAudioFileProviderFactory = mock(SharedStorageAudioFileProviderFactory.class);
    grpcService =
        new SpeechToTextGrpcService(speechToTextService, sharedStorageAudioFileProviderFactory);
  }

  @Test
  public void shouldStartSpeechToTextTaskSuccessfully() throws Exception {
    when(speechToTextService.createSpeechToTextTask(any(SpeechToTextTaskConfiguration.class)))
        .thenReturn(42L);
    TestResponseObserver<SpeechToTextService.TaskDescription> responseObserver =
        new TestResponseObserver<>();

    grpcService.startSpeechToTextTask(createTaskRequest(), responseObserver);

    verify(speechToTextService, times(1))
        .createSpeechToTextTask(
            argThat(
                configuration ->
                    configuration.engineType() == SpeechToTextEngineType.WHISPER_LOCAL
                        && Locale.GERMAN.equals(configuration.locale())
                        && "base".equals(configuration.modelIdentifier())
                        && configuration.outputFormat() == SpeechToTextEngineOutputFormat.SRT));
    assertEquals(1, responseObserver.values.size());
    assertEquals(42L, responseObserver.values.getFirst().getTaskId());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnInvalidArgumentWhenStartingSpeechToTextTaskFails() throws Exception {
    when(speechToTextService.createSpeechToTextTask(any(SpeechToTextTaskConfiguration.class)))
        .thenThrow(new IllegalArgumentException("Unsupported model"));
    TestResponseObserver<SpeechToTextService.TaskDescription> responseObserver =
        new TestResponseObserver<>();

    grpcService.startSpeechToTextTask(createTaskRequest(), responseObserver);

    assertStatus(responseObserver.error, Status.Code.INVALID_ARGUMENT, "Unsupported model");
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldPersistAudioFileStreamSuccessfully() throws Exception {
    AudioFileChunkConsumer audioFileChunkConsumer = mock(AudioFileChunkConsumer.class);
    when(speechToTextService.createAudioFileChunkConsumer(7L)).thenReturn(audioFileChunkConsumer);
    TestResponseObserver<Empty> responseObserver = new TestResponseObserver<>();

    StreamObserver<SpeechToTextService.AudioFileChunk> requestObserver =
        grpcService.provideAudioFileStream(responseObserver);

    requestObserver.onNext(createAudioChunk(7L, 0, false, "first"));
    requestObserver.onNext(createAudioChunk(7L, 1, true, "second"));
    requestObserver.onCompleted();

    ArgumentCaptor<AudioFileChunk> chunkCaptor = ArgumentCaptor.forClass(AudioFileChunk.class);
    verify(audioFileChunkConsumer, times(2)).consume(chunkCaptor.capture());
    verify(audioFileChunkConsumer, times(1)).close();

    List<AudioFileChunk> persistedChunks = chunkCaptor.getAllValues();
    assertEquals(2, persistedChunks.size());
    assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), persistedChunks.get(0).content());
    assertFalse(persistedChunks.get(0).lastChunk());
    assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), persistedChunks.get(1).content());
    assertTrue(persistedChunks.get(1).lastChunk());

    assertEquals(1, responseObserver.values.size());
    assertEquals(Empty.getDefaultInstance(), responseObserver.values.getFirst());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldRejectAudioFileStreamWhenChunkIndexIsUnexpected() throws Exception {
    AudioFileChunkConsumer audioFileChunkConsumer = mock(AudioFileChunkConsumer.class);
    when(speechToTextService.createAudioFileChunkConsumer(7L)).thenReturn(audioFileChunkConsumer);
    TestResponseObserver<Empty> responseObserver = new TestResponseObserver<>();

    StreamObserver<SpeechToTextService.AudioFileChunk> requestObserver =
        grpcService.provideAudioFileStream(responseObserver);

    requestObserver.onNext(createAudioChunk(7L, 1, false, "invalid"));

    assertStatus(
        responseObserver.error,
        Status.Code.INVALID_ARGUMENT,
        "Unexpected audio chunk index. Expected 0 but received 1");
    verify(audioFileChunkConsumer, never()).consume(any(AudioFileChunk.class));
    verify(audioFileChunkConsumer, times(1)).close();
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldProvideSharedAudioFileSuccessfully() throws Exception {
    AudioFileProvider audioFileProvider = mock(AudioFileProvider.class);
    when(sharedStorageAudioFileProviderFactory.createProviderForSharedStorageFile(13L, "audio.wav"))
        .thenReturn(audioFileProvider);
    TestResponseObserver<Empty> responseObserver = new TestResponseObserver<>();

    grpcService.provideSharedAudioFile(
        SpeechToTextService.SharedAudioFile.newBuilder()
            .setTaskId(13L)
            .setFileName("audio.wav")
            .build(),
        responseObserver);

    verify(sharedStorageAudioFileProviderFactory, times(1))
        .createProviderForSharedStorageFile(13L, "audio.wav");
    verify(speechToTextService, times(1)).provideAudioFile(audioFileProvider);
    assertEquals(1, responseObserver.values.size());
    assertEquals(Empty.getDefaultInstance(), responseObserver.values.getFirst());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnNotFoundWhenProvidingSharedAudioFileFails() throws Exception {
    AudioFileProvider audioFileProvider = mock(AudioFileProvider.class);
    when(sharedStorageAudioFileProviderFactory.createProviderForSharedStorageFile(13L, "audio.wav"))
        .thenReturn(audioFileProvider);
    doThrow(new NotFoundStorageException("task", 13L))
        .when(speechToTextService)
        .provideAudioFile(audioFileProvider);
    TestResponseObserver<Empty> responseObserver = new TestResponseObserver<>();

    grpcService.provideSharedAudioFile(
        SpeechToTextService.SharedAudioFile.newBuilder()
            .setTaskId(13L)
            .setFileName("audio.wav")
            .build(),
        responseObserver);

    assertStatus(responseObserver.error, Status.Code.NOT_FOUND, null);
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldLoadTaskStatusSuccessfully() throws Exception {
    when(speechToTextService.getSpeechToTextTaskState(9L)).thenReturn(SpeechToTextTaskState.RUNNING);
    TestResponseObserver<SpeechToTextService.TaskState> responseObserver =
        new TestResponseObserver<>();

    grpcService.getTaskStatus(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(9L).build(), responseObserver);

    assertEquals(1, responseObserver.values.size());
    assertEquals(
        SpeechToTextService.SpeechToTextTaskState.RUNNING,
        responseObserver.values.getFirst().getTaskState());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnNotFoundWhenLoadingTaskStatusFails() throws Exception {
    when(speechToTextService.getSpeechToTextTaskState(9L))
        .thenThrow(new NotFoundStorageException("task", 9L));
    TestResponseObserver<SpeechToTextService.TaskState> responseObserver =
        new TestResponseObserver<>();

    grpcService.getTaskStatus(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(9L).build(), responseObserver);

    assertStatus(responseObserver.error, Status.Code.NOT_FOUND, null);
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldWriteTaskResultsIntoSharedStorageSuccessfully() throws Exception {
    when(speechToTextService.moveResultsOfTasksToSharedStorage(27L))
        .thenReturn(Path.of("/shared/task-27.srt"));
    TestResponseObserver<SpeechToTextService.SharedStorageResult> responseObserver =
        new TestResponseObserver<>();

    grpcService.writeTaskResultsIntoSharedStorage(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(27L).build(), responseObserver);

    assertEquals(1, responseObserver.values.size());
    assertEquals("task-27.srt", responseObserver.values.getFirst().getFileName());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnFailedPreconditionWhenWritingTaskResultsIntoSharedStorageFails()
      throws Exception {
    when(speechToTextService.moveResultsOfTasksToSharedStorage(27L))
        .thenThrow(new IllegalStateException("Task not completed"));
    TestResponseObserver<SpeechToTextService.SharedStorageResult> responseObserver =
        new TestResponseObserver<>();

    grpcService.writeTaskResultsIntoSharedStorage(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(27L).build(), responseObserver);

    assertStatus(responseObserver.error, Status.Code.FAILED_PRECONDITION, null);
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldStreamTaskResultsSuccessfully() throws Exception {
    Path resultFile = tempDir.resolve("result.srt");
    Files.writeString(resultFile, "hello", StandardCharsets.UTF_8);
    SpeechToTextTaskResultConsumer taskResultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    when(taskResultConsumer.getResultFilePath()).thenReturn(resultFile);
    when(speechToTextService.consumeSpeechToTextTaskResults(33L)).thenReturn(taskResultConsumer);
    TestResponseObserver<SpeechToTextService.SpeechToTextResultFileChunk> responseObserver =
        new TestResponseObserver<>();

    grpcService.getTaskResultsAsStream(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(33L).build(), responseObserver);

    verify(taskResultConsumer, times(1)).close();
    assertEquals(1, responseObserver.values.size());
    SpeechToTextService.SpeechToTextResultFileChunk chunk = responseObserver.values.getFirst();
    assertEquals(SpeechToTextService.SpeechToTextEngineOutputFormat.SRT, chunk.getOutputFormat());
    assertEquals(5L, chunk.getFileSize());
    assertEquals(0, chunk.getChunkIndex());
    assertTrue(chunk.getLastChunk());
    assertEquals("hello", chunk.getContent().toString(StandardCharsets.UTF_8));
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldStreamLargeTaskResultsInMultipleChunksSuccessfully() throws Exception {
    byte[] content = new byte[70_000];
    for (int index = 0; index < content.length; index++) {
      content[index] = (byte) (index % 251);
    }

    Path resultFile = tempDir.resolve("result-large.srt");
    Files.write(resultFile, content);
    SpeechToTextTaskResultConsumer taskResultConsumer = mock(SpeechToTextTaskResultConsumer.class);
    when(taskResultConsumer.getResultFilePath()).thenReturn(resultFile);
    when(speechToTextService.consumeSpeechToTextTaskResults(34L)).thenReturn(taskResultConsumer);
    TestResponseObserver<SpeechToTextService.SpeechToTextResultFileChunk> responseObserver =
        new TestResponseObserver<>();

    grpcService.getTaskResultsAsStream(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(34L).build(), responseObserver);

    verify(taskResultConsumer, times(1)).close();
    assertEquals(2, responseObserver.values.size());
    assertFalse(responseObserver.values.get(0).getLastChunk());
    assertTrue(responseObserver.values.get(1).getLastChunk());

    ByteArrayOutputStream reconstructedContent = new ByteArrayOutputStream();
    for (SpeechToTextService.SpeechToTextResultFileChunk chunk : responseObserver.values) {
      reconstructedContent.write(chunk.getContent().toByteArray());
    }

    assertArrayEquals(content, reconstructedContent.toByteArray());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnNotFoundWhenStreamingTaskResultsFails() throws Exception {
    when(speechToTextService.consumeSpeechToTextTaskResults(33L))
        .thenThrow(new NotFoundStorageException("task", 33L));
    TestResponseObserver<SpeechToTextService.SpeechToTextResultFileChunk> responseObserver =
        new TestResponseObserver<>();

    grpcService.getTaskResultsAsStream(
        SpeechToTextService.TaskDescription.newBuilder().setTaskId(33L).build(), responseObserver);

    assertStatus(responseObserver.error, Status.Code.NOT_FOUND, null);
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  @Test
  public void shouldGetSpeechToTextEnginesSuccessfully() {
    SpeechToTextEngine speechToTextEngine = mock(SpeechToTextEngine.class);
    when(speechToTextEngine.getSpeechToTextEngineType()).thenReturn(SpeechToTextEngineType.WHISPER_LOCAL);
    when(speechToTextEngine.getModels())
        .thenReturn(List.of(new SpeechToTextEngineModel("base"), new SpeechToTextEngineModel("small")));
    when(speechToTextEngine.getAllowedOutputFormats())
        .thenReturn(List.of(SpeechToTextEngineOutputFormat.SRT, SpeechToTextEngineOutputFormat.JSON));
    when(speechToTextService.getSpeechToTextEngines()).thenReturn(List.of(speechToTextEngine));
    TestResponseObserver<SpeechToTextService.SpeechToTextEngines> responseObserver =
        new TestResponseObserver<>();

    grpcService.getEngines(Empty.getDefaultInstance(), responseObserver);

    assertEquals(1, responseObserver.values.size());
    SpeechToTextService.SpeechToTextEngines.SpeechToTextEngine grpcEngine =
        responseObserver.values.getFirst().getSpeechToTextEngines(0);
    assertEquals(SpeechToTextEngineType.WHISPER_LOCAL.name(), grpcEngine.getEngineIdentifier());
    assertEquals(SpeechToTextEngineType.WHISPER_LOCAL.getEngineTypeName(), grpcEngine.getEngineName());
    assertEquals(List.of("base", "small"), grpcEngine.getModelIdentifiersList());
    assertEquals(
        List.of(
            SpeechToTextService.SpeechToTextEngineOutputFormat.SRT,
            SpeechToTextService.SpeechToTextEngineOutputFormat.JSON),
        grpcEngine.getAllowedOutputFormatsList());
    assertTrue(responseObserver.completed);
    assertNull(responseObserver.error);
  }

  @Test
  public void shouldReturnInternalErrorWhenGettingSpeechToTextEnginesFails() {
    when(speechToTextService.getSpeechToTextEngines()).thenThrow(new RuntimeException("Failure"));
    TestResponseObserver<SpeechToTextService.SpeechToTextEngines> responseObserver =
        new TestResponseObserver<>();

    grpcService.getEngines(Empty.getDefaultInstance(), responseObserver);

    assertStatus(responseObserver.error, Status.Code.INTERNAL, null);
    assertFalse(responseObserver.completed);
    assertTrue(responseObserver.values.isEmpty());
  }

  private static SpeechToTextService.TaskRequest createTaskRequest() {
    return SpeechToTextService.TaskRequest.newBuilder()
        .setEngineIdentifier(SpeechToTextEngineType.WHISPER_LOCAL.name())
        .setModelIdentifier("base")
        .setOutputFormat(SpeechToTextService.SpeechToTextEngineOutputFormat.SRT)
        .setLocale(Locale.GERMAN.toLanguageTag())
        .build();
  }

  private static SpeechToTextService.AudioFileChunk createAudioChunk(
      long taskId, int chunkIndex, boolean lastChunk, String content) {
    return SpeechToTextService.AudioFileChunk.newBuilder()
        .setTaskId(taskId)
        .setChunkIndex(chunkIndex)
        .setLastChunk(lastChunk)
        .setContent(ByteString.copyFromUtf8(content))
        .build();
  }

  private static void assertStatus(
      Throwable throwable, Status.Code expectedStatusCode, String expectedDescription) {
    assertNotNull(throwable);
    RuntimeException runtimeException = assertInstanceOf(RuntimeException.class, throwable);
    Status status = Status.fromThrowable(runtimeException);
    assertEquals(expectedStatusCode, status.getCode());
    if (expectedDescription == null) {
      assertNull(status.getDescription());
    } else {
      assertEquals(expectedDescription, status.getDescription());
    }
  }

  private static final class TestResponseObserver<T> implements StreamObserver<T> {

    private final List<T> values = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
