package studio.nkodev.stt.client.adapter.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ChannelCredentials;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferType;
import studio.nkodev.stt.client.exception.SpeechToTextServiceClientException;
import studio.nkodev.stt.client.adapter.SpeechToTextServiceAdapter;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.proto.SpeechToTextGrpc;
import studio.nkodev.stt.proto.SpeechToTextService;

/**
 * gRPC based implementation of the {@link SpeechToTextServiceAdapter}.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public class GrpcSpeechToTextServiceAdapter implements SpeechToTextServiceAdapter {

  private static final Logger logger =
      LoggerFactory.getLogger(GrpcSpeechToTextServiceAdapter.class);
  private static final long STREAM_COMPLETION_TIMEOUT_SECONDS = 30L;
  private static final long STREAM_READY_POLL_INTERVAL_MS = 10L;

  private final SpeechToTextServiceClientConfiguration configuration;
  private final ManagedChannel channel;
  private final SpeechToTextGrpc.SpeechToTextBlockingStub blockingStub;
  private final SpeechToTextGrpc.SpeechToTextStub asyncStub;

  public GrpcSpeechToTextServiceAdapter(SpeechToTextServiceClientConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "No client configuration provided");
    this.channel = createChannel(configuration);
    this.blockingStub = SpeechToTextGrpc.newBlockingStub(channel);
    this.asyncStub = SpeechToTextGrpc.newStub(channel);
  }

  @Override
  public long startSpeechToTextTask(
      Path audioFile,
      String engineIdentifier,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    verifyAudioFile(audioFile);

    SpeechToTextService.TaskRequest taskRequest =
        createTaskRequest(engineIdentifier, modelIdentifier, outputFormat, locale);

    try {
      long taskId = blockingStub.startSpeechToTextTask(taskRequest).getTaskId();
      provideAudioFile(taskId, audioFile);
      return taskId;
    } catch (StatusRuntimeException exception) {
      throw createClientException("Failed to start speech-to-text task", exception);
    }
  }

  @Override
  public SpeechToTextTaskState getStateOfTask(long taskId) {
    try {
      SpeechToTextService.TaskState taskState =
          blockingStub.getTaskStatus(createTaskDescription(taskId));
      return GrpcSpeechToTextMapper.mapTaskState(taskState.getTaskState());
    } catch (StatusRuntimeException exception) {
      throw createClientException("Failed to load task state of task " + taskId, exception);
    }
  }

  @Override
  public void saveResultsOfTask(long taskId, Path resultFile) {
    Objects.requireNonNull(resultFile, "No result file path provided");

    try {
      if (SpeechToTextTransferType.SHARED_STORAGE.equals(
          configuration.resultTransferConfiguration().transferType())) {
        persistSharedStorageResults(taskId, resultFile);
      } else {
        persistStreamedResults(taskId, resultFile);
      }
    } catch (StatusRuntimeException exception) {
      throw createClientException("Failed to save results of task " + taskId, exception);
    }
  }

  @Override
  public Collection<SpeechToTextEngine> getAvailableEngines() {
    try {
      return blockingStub
          .getEngines(Empty.getDefaultInstance())
          .getSpeechToTextEnginesList()
          .stream()
          .map(GrpcSpeechToTextServiceAdapter::mapSpeechToTextEngine)
          .toList();
    } catch (StatusRuntimeException exception) {
      throw createClientException("Failed to get available engines", exception);
    } catch (Exception exception) {
      throw new SpeechToTextServiceClientException("Failed to get available engines", exception);
    }
  }

  private static SpeechToTextEngine mapSpeechToTextEngine(
      SpeechToTextService.SpeechToTextEngines.SpeechToTextEngine speechToTextEngine) {
    Collection<SpeechToTextEngineOutputFormat> allowedOutputFormats =
        speechToTextEngine.getAllowedOutputFormatsList().stream()
            .map(GrpcSpeechToTextMapper::mapOutputFormat)
            .toList();
    return new SpeechToTextEngine(
        speechToTextEngine.getEngineIdentifier(),
        speechToTextEngine.getEngineName(),
        speechToTextEngine.getModelIdentifiersList(),
        allowedOutputFormats);
  }

  @Override
  public void close() {
    channel.shutdownNow();
  }

  private SpeechToTextService.TaskRequest createTaskRequest(
      String engineIdentifier,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    if (modelIdentifier == null || modelIdentifier.isBlank()) {
      throw new IllegalArgumentException("No model identifier provided");
    }
    if (outputFormat == null) {
      throw new IllegalArgumentException("No engine output format provided");
    }

    SpeechToTextService.TaskRequest.Builder builder =
        SpeechToTextService.TaskRequest.newBuilder()
            .setEngineIdentifier(engineIdentifier)
            .setModelIdentifier(modelIdentifier)
            .setOutputFormat(GrpcSpeechToTextMapper.mapOutputFormat(outputFormat));
    if (locale != null) {
      builder.setLocale(locale.toLanguageTag());
    }

    return builder.build();
  }

  private void provideAudioFile(long taskId, Path audioFile) {
    if (configuration.audioFileTransferConfiguration().transferType()
        == SpeechToTextTransferType.SHARED_STORAGE) {
      provideAudioFileBySharedStorage(taskId, audioFile);
      return;
    }

    provideAudioFileByStream(taskId, audioFile);
  }

  private void provideAudioFileBySharedStorage(long taskId, Path audioFile) {
    Path sharedStorageFile =
        configuration
            .audioFileTransferConfiguration()
            .sharedStorageDirectory()
            .resolve(
                taskId
                    + "-"
                    + UUID.randomUUID()
                    + createOptionalFileSuffixSeparator(extractFileSuffix(audioFile)));
    try {
      Files.copy(audioFile, sharedStorageFile, StandardCopyOption.REPLACE_EXISTING);
      blockingStub.provideSharedAudioFile(
          SpeechToTextService.SharedAudioFile.newBuilder()
              .setTaskId(taskId)
              .setFileName(sharedStorageFile.getFileName().toString())
              .build());
    } catch (IOException exception) {
      throw new SpeechToTextServiceClientException(
          "Failed to copy audio file into shared storage", exception);
    } catch (StatusRuntimeException exception) {
      try {
        Files.deleteIfExists(sharedStorageFile);
      } catch (IOException ioException) {
        logger.warn("Failed to clean shared audio upload {}", sharedStorageFile, ioException);
      }
      throw createClientException(
          "Failed to provide shared audio file for task " + taskId, exception);
    }
  }

  private void provideAudioFileByStream(long taskId, Path audioFile) {
    CountDownLatch completionLatch = new CountDownLatch(1);
    AtomicReference<Throwable> failureReference = new AtomicReference<>();
    StreamObserver<Empty> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(Empty value) {}

          @Override
          public void onError(Throwable throwable) {
            failureReference.set(throwable);
            completionLatch.countDown();
          }

          @Override
          public void onCompleted() {
            completionLatch.countDown();
          }
        };
    StreamObserver<SpeechToTextService.AudioFileChunk> requestObserver =
        asyncStub.provideAudioFileStream(responseObserver);

    try (InputStream inputStream = Files.newInputStream(audioFile)) {
      streamAudioFileChunks(
          taskId, inputStream, requestObserver, configuration.audioFileChunkSizeBytes());
      requestObserver.onCompleted();
      awaitStreamCompletion(completionLatch, failureReference, "audio file upload");
    } catch (IOException exception) {
      requestObserver.onError(exception);
      throw new SpeechToTextServiceClientException(
          "Failed to read audio file " + audioFile + " for upload", exception);
    } catch (RuntimeException exception) {
      requestObserver.onError(exception);
      throw exception;
    }
  }

  static void streamAudioFileChunks(
      long taskId,
      InputStream inputStream,
      StreamObserver<SpeechToTextService.AudioFileChunk> requestObserver,
      int chunkSizeBytes)
      throws IOException {
    byte[] currentChunkBuffer = new byte[chunkSizeBytes];
    int currentChunkSize = inputStream.read(currentChunkBuffer);

    if (currentChunkSize == -1) {
      requestObserver.onNext(
          SpeechToTextService.AudioFileChunk.newBuilder()
              .setTaskId(taskId)
              .setChunkIndex(0)
              .setLastChunk(true)
              .setContent(ByteString.EMPTY)
              .build());
      return;
    }

    int chunkIndex = 0;
    while (true) {
      byte[] nextChunkBuffer = new byte[chunkSizeBytes];
      int nextChunkSize = inputStream.read(nextChunkBuffer);

      requestObserver.onNext(
          SpeechToTextService.AudioFileChunk.newBuilder()
              .setTaskId(taskId)
              .setChunkIndex(chunkIndex++)
              .setLastChunk(nextChunkSize == -1)
              .setContent(ByteString.copyFrom(currentChunkBuffer, 0, currentChunkSize))
              .build());

      if (nextChunkSize == -1) {
        return;
      }

      currentChunkBuffer = nextChunkBuffer;
      currentChunkSize = nextChunkSize;
    }
  }

  private void persistSharedStorageResults(long taskId, Path resultFilePath) {
    SpeechToTextService.SharedStorageResult sharedStorageResult =
        blockingStub.writeTaskResultsIntoSharedStorage(createTaskDescription(taskId));
    Path sourcePath =
        configuration
            .resultTransferConfiguration()
            .sharedStorageDirectory()
            .resolve(sharedStorageResult.getFileName());
    try {
      Files.move(sourcePath, resultFilePath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new SpeechToTextServiceClientException(
          "Failed to move result file from shared storage " + sourcePath, exception);
    }
  }

  private void persistStreamedResults(long taskId, Path resultPath) {
    Iterator<SpeechToTextService.SpeechToTextResultFileChunk> resultIterator =
        blockingStub.getTaskResultsAsStream(createTaskDescription(taskId));

    OutputStream outputStream = null;
    SpeechToTextEngineOutputFormat outputFormat = null;
    int expectedChunkIndex = 0;
    boolean receivedChunks = false;
    boolean lastChunkReceived = false;

    try {
      while (resultIterator.hasNext()) {
        SpeechToTextService.SpeechToTextResultFileChunk resultChunk = resultIterator.next();
        if (lastChunkReceived) {
          throw new SpeechToTextServiceClientException(
              "Received multiple result files for task " + taskId);
        }

        SpeechToTextEngineOutputFormat currentOutputFormat =
            SpeechToTextEngineOutputFormat.valueOf(resultChunk.getOutputFormat().name());
        if (outputFormat == null) {
          outputFormat = currentOutputFormat;
          outputStream = createOutputStream(resultPath);
        } else if (outputFormat != currentOutputFormat) {
          throw new SpeechToTextServiceClientException(
              "Received inconsistent output formats while reading results of task " + taskId);
        }

        validateChunkIndex(expectedChunkIndex, resultChunk.getChunkIndex(), taskId);

        if (!resultChunk.getContent().isEmpty()) {
          outputStream.write(resultChunk.getContent().toByteArray());
        }

        receivedChunks = true;
        expectedChunkIndex = resultChunk.getChunkIndex() + 1;
        if (resultChunk.getLastChunk()) {
          outputStream.close();
          outputStream = null;
          lastChunkReceived = true;
        }
      }

      if (!receivedChunks) {
        throw new SpeechToTextServiceClientException(
            "Received no result stream for task " + taskId);
      }
      if (!lastChunkReceived) {
        throw new SpeechToTextServiceClientException(
            "Received an incomplete result stream for task " + taskId);
      }
    } catch (IOException exception) {
      throw new SpeechToTextServiceClientException(
          "Failed to persist streamed results of task " + taskId, exception);
    } finally {
      closeQuietly(outputStream);
    }
  }

  private static void validateChunkIndex(int expectedChunkIndex, int chunkIndex, long taskId) {
    if (chunkIndex != expectedChunkIndex) {
      throw new SpeechToTextServiceClientException(
          "Unexpected result chunk index while reading results of task "
              + taskId
              + ". Expected "
              + expectedChunkIndex
              + " but received "
              + chunkIndex);
    }
  }

  private static OutputStream createOutputStream(Path resultFilePath) {
    try {
      return Files.newOutputStream(
          resultFilePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException exception) {
      throw new SpeechToTextServiceClientException(
          "Failed to create result file " + resultFilePath, exception);
    }
  }

  private static SpeechToTextService.TaskDescription createTaskDescription(long taskId) {
    return SpeechToTextService.TaskDescription.newBuilder().setTaskId(taskId).build();
  }

  private static void verifyAudioFile(Path audioFile) {
    if (audioFile == null || !Files.isRegularFile(audioFile)) {
      throw new IllegalArgumentException("No valid audio file provided");
    }
  }

  private static String extractFileSuffix(Path file) {
    String fileName = file.getFileName().toString();
    int suffixSeparatorIndex = fileName.lastIndexOf('.');
    return suffixSeparatorIndex >= 0 ? fileName.substring(suffixSeparatorIndex + 1) : "";
  }

  private static String createOptionalFileSuffixSeparator(String fileSuffix) {
    return fileSuffix == null || fileSuffix.isBlank() ? "" : "." + fileSuffix;
  }

  private static ManagedChannel createChannel(
      SpeechToTextServiceClientConfiguration configuration) {
    return Grpc.newChannelBuilderForAddress(
            configuration.host(), configuration.port(), createChannelCredentials(configuration))
        .overrideAuthority(
            configuration.authorityOverride() == null
                ? configuration.host()
                : configuration.authorityOverride())
        .build();
  }

  private static ChannelCredentials createChannelCredentials(
      SpeechToTextServiceClientConfiguration configuration) {
    try {
      TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder();
      if (configuration.trustedServerCertificatePath() != null) {
        builder.trustManager(configuration.trustedServerCertificatePath().toFile());
      }
      if (configuration.authenticationConfiguration() != null) {
        builder.keyManager(
            configuration.authenticationConfiguration().certificatePath().toFile(),
            configuration.authenticationConfiguration().privateKeyPath().toFile(),
            configuration.authenticationConfiguration().privateKeyPassword());
      }
      return builder.build();
    } catch (IOException exception) {
      throw new SpeechToTextServiceClientException(
          "Failed to initialize gRPC TLS channel credentials", exception);
    }
  }

  private static SpeechToTextServiceClientException createClientException(
      String message, StatusRuntimeException exception) {
    String description =
        exception.getStatus().getDescription() == null
            ? exception.getStatus().getCode().name()
            : exception.getStatus().getDescription();
    return new SpeechToTextServiceClientException(message + ": " + description, exception);
  }

  private static void awaitStreamCompletion(
      CountDownLatch completionLatch,
      AtomicReference<Throwable> failureReference,
      String operationDescription) {
    try {
      if (!completionLatch.await(STREAM_COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new SpeechToTextServiceClientException(
            "Timed out while waiting for " + operationDescription + " to complete");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SpeechToTextServiceClientException(
          "Interrupted while waiting for " + operationDescription + " to complete", exception);
    }

    if (failureReference.get() != null) {
      Throwable throwable = failureReference.get();
      if (throwable instanceof StatusRuntimeException statusRuntimeException) {
        throw createClientException(
            "Failed during " + operationDescription, statusRuntimeException);
      }
      throw new SpeechToTextServiceClientException(
          "Failed during " + operationDescription, throwable);
    }
  }

  private static void closeQuietly(OutputStream outputStream) {
    if (outputStream == null) {
      return;
    }
    try {
      outputStream.close();
    } catch (IOException exception) {
      logger.warn("Failed to close result output stream", exception);
    }
  }
}
