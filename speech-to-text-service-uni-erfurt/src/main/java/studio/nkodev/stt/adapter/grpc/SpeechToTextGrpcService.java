package studio.nkodev.stt.adapter.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineModel;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.proto.SpeechToTextGrpc.SpeechToTextImplBase;
import studio.nkodev.stt.proto.SpeechToTextService.SharedStorageResult;
import studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextResultFileChunk;
import studio.nkodev.stt.proto.SpeechToTextService.TaskDescription;
import studio.nkodev.stt.proto.SpeechToTextService.TaskState;
import studio.nkodev.stt.service.SpeechToTextService;
import studio.nkodev.stt.storage.audio.AudioFileChunk;
import studio.nkodev.stt.storage.audio.AudioFileChunkConsumer;
import studio.nkodev.stt.storage.audio.AudioFileProvider;
import studio.nkodev.stt.storage.audio.SharedStorageAudioFileProviderFactory;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultConsumer;

/**
 * This service handles requests received via gRPC
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 14.03.26
 */
class SpeechToTextGrpcService extends SpeechToTextImplBase {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextGrpcService.class);
  private static final int CHUNK_SIZE_BYTES = 64 * 1024;

  private final SpeechToTextService speechToTextService;
  private final SharedStorageAudioFileProviderFactory sharedStorageAudioFileProviderFactory;

  public SpeechToTextGrpcService(
      SpeechToTextService speechToTextService,
      SharedStorageAudioFileProviderFactory sharedStorageAudioFileProviderFactory) {
    this.speechToTextService = speechToTextService;
    this.sharedStorageAudioFileProviderFactory = sharedStorageAudioFileProviderFactory;
  }

  @Override
  public void startSpeechToTextTask(
      studio.nkodev.stt.proto.SpeechToTextService.TaskRequest request,
      io.grpc.stub.StreamObserver<studio.nkodev.stt.proto.SpeechToTextService.TaskDescription>
          responseObserver) {
    logger.info("Received request to start speech to text task");
    try {
      long createdTaskId =
          speechToTextService.createSpeechToTextTask(mapTaskConfiguration(request));
      responseObserver.onNext(TaskDescription.newBuilder().setTaskId(createdTaskId).build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  @Override
  public StreamObserver<studio.nkodev.stt.proto.SpeechToTextService.AudioFileChunk>
      provideAudioFileStream(StreamObserver<com.google.protobuf.Empty> responseObserver) {
    logger.info("Received request to persist audio file stream");
    return new StreamObserver<>() {

      private AudioFileChunkConsumer audioFileChunkConsumer;
      private Long taskId;
      private int expectedChunkIndex;
      private boolean lastChunkReceived;
      private boolean failed;

      @Override
      public void onNext(studio.nkodev.stt.proto.SpeechToTextService.AudioFileChunk value) {
        if (failed) {
          return;
        }

        try {
          initializeConsumerIfRequired(value.getTaskId());
          verifyTaskId(value.getTaskId());
          verifyChunkIndex(value.getChunkIndex());

          audioFileChunkConsumer.consume(
              new AudioFileChunk(value.getContent().toByteArray(), value.getLastChunk()));

          expectedChunkIndex++;
          if (value.getLastChunk()) {
            lastChunkReceived = true;
          }
        } catch (NotFoundStorageException ex) {
          fail(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
          fail(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
          fail(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
          logger.error("Failed to persist audio file stream", ex);
          fail(Status.INTERNAL.asRuntimeException());
        }
      }

      @Override
      public void onError(Throwable t) {
        closeChunkConsumer();
      }

      @Override
      public void onCompleted() {
        if (!lastChunkReceived) {
          fail(
              Status.INVALID_ARGUMENT
                  .withDescription("Audio file stream completed before the last chunk was sent")
                  .asRuntimeException());
          return;
        }

        closeChunkConsumer();
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
      }

      private void initializeConsumerIfRequired(long receivedTaskId) throws Exception {
        if (audioFileChunkConsumer == null) {
          taskId = receivedTaskId;
          audioFileChunkConsumer = speechToTextService.createAudioFileChunkConsumer(receivedTaskId);
        }
      }

      private void verifyTaskId(long receivedTaskId) {
        if (!taskId.equals(receivedTaskId)) {
          throw new IllegalArgumentException("All audio chunks of a stream must target one task");
        }
      }

      private void verifyChunkIndex(int chunkIndex) {
        if (chunkIndex != expectedChunkIndex) {
          throw new IllegalArgumentException(
              "Unexpected audio chunk index. Expected "
                  + expectedChunkIndex
                  + " but received "
                  + chunkIndex);
        }
      }

      private void fail(RuntimeException runtimeException) {
        failed = true;
        closeChunkConsumer();
        responseObserver.onError(runtimeException);
      }

      private void closeChunkConsumer() {
        if (audioFileChunkConsumer == null) {
          return;
        }

        try {
          audioFileChunkConsumer.close();
        } catch (Exception ex) {
          logger.warn("Failed to close audio file chunk consumer", ex);
        } finally {
          audioFileChunkConsumer = null;
        }
      }
    };
  }

  @Override
  public void provideSharedAudioFile(
      studio.nkodev.stt.proto.SpeechToTextService.SharedAudioFile request,
      io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
    logger.info("Received request to provide shared audio file for task {}", request.getTaskId());

    try {
      AudioFileProvider audioFileProvider =
          sharedStorageAudioFileProviderFactory.createProviderForSharedStorageFile(
              request.getTaskId(), request.getFileName());
      speechToTextService.provideAudioFile(audioFileProvider);
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (NotFoundStorageException ex) {
      responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
    } catch (Exception ex) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  @Override
  public void getTaskStatus(
      studio.nkodev.stt.proto.SpeechToTextService.TaskDescription request,
      io.grpc.stub.StreamObserver<studio.nkodev.stt.proto.SpeechToTextService.TaskState>
          responseObserver) {
    logger.info("Received request to load task status of task {}", request.getTaskId());

    try {
      SpeechToTextTaskState taskState =
          speechToTextService.getSpeechToTextTaskState(request.getTaskId());
      TaskState result = TaskState.newBuilder().setTaskState(mapTaskState(taskState)).build();
      responseObserver.onNext(result);
      responseObserver.onCompleted();

    } catch (NotFoundStorageException ex) {
      responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
    } catch (Exception ex) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  @Override
  public void writeTaskResultsIntoSharedStorage(
      studio.nkodev.stt.proto.SpeechToTextService.TaskDescription request,
      io.grpc.stub.StreamObserver<studio.nkodev.stt.proto.SpeechToTextService.SharedStorageResult>
          responseObserver) {
    logger.info(
        "Received request to write task results of task {} into shared storage",
        request.getTaskId());

    try {
      Path sharedStorageLocation =
          speechToTextService.moveResultsOfTasksToSharedStorage(request.getTaskId());
      SharedStorageResult sharedStorageResult =
          SharedStorageResult.newBuilder()
              .setFileName(sharedStorageLocation.getFileName().toString())
              .build();

      responseObserver.onNext(sharedStorageResult);
      responseObserver.onCompleted();
    } catch (NotFoundStorageException e) {
      responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.asRuntimeException());
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  @Override
  public void getTaskResultsAsStream(
      studio.nkodev.stt.proto.SpeechToTextService.TaskDescription request,
      io.grpc.stub.StreamObserver<
              studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextResultFileChunk>
          responseObserver) {
    logger.info(
        "Received request to provide task results of task {} as stream", request.getTaskId());

    try (SpeechToTextTaskResultConsumer speechToTextTaskResultConsumer =
        speechToTextService.consumeSpeechToTextTaskResults(request.getTaskId())) {
      Path resultFile = speechToTextTaskResultConsumer.getResultFilePath();
      transferFileInResultFileChunks(resultFile, responseObserver);

      responseObserver.onCompleted();
    } catch (NotFoundStorageException e) {
      responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.asRuntimeException());
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  private static void transferFileInResultFileChunks(
      Path resultFile,
      io.grpc.stub.StreamObserver<
              studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextResultFileChunk>
          responseObserver)
      throws IOException {
    logger.debug("Transferring file {} in chunks", resultFile.toString());

    studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngineOutputFormat outputFormat =
        resolveOutputFormat(resultFile);
    long fileSize = Files.size(resultFile);
    try (InputStream inputStream = Files.newInputStream(resultFile)) {
      byte[] currentChunkBuffer = new byte[CHUNK_SIZE_BYTES];
      int currentChunkSize = inputStream.read(currentChunkBuffer);

      if (currentChunkSize == -1) {
        responseObserver.onNext(
            SpeechToTextResultFileChunk.newBuilder()
                .setLastChunk(true)
                .setFileSize(0L)
                .setChunkIndex(0)
                .setOutputFormat(outputFormat)
                .setContent(ByteString.EMPTY)
                .build());
        return;
      }

      int chunkIndex = 0;
      int nextChunkSize;
      do {
        byte[] nextChunkBuffer = new byte[CHUNK_SIZE_BYTES];
        nextChunkSize = inputStream.read(nextChunkBuffer);

        responseObserver.onNext(
            SpeechToTextResultFileChunk.newBuilder()
                .setLastChunk(nextChunkSize == -1)
                .setFileSize(fileSize)
                .setChunkIndex(chunkIndex++)
                .setOutputFormat(outputFormat)
                .setContent(ByteString.copyFrom(currentChunkBuffer, 0, currentChunkSize))
                .build());

        if (nextChunkSize != -1) {
          currentChunkBuffer = nextChunkBuffer;
          currentChunkSize = nextChunkSize;
        }
      } while (nextChunkSize != -1);
    }
  }

  private static studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngineOutputFormat
      resolveOutputFormat(Path resultFile) {
    String fileName = resultFile.getFileName().toString();
    int extensionStartIndex = fileName.lastIndexOf('.');
    String fileSuffix = extensionStartIndex >= 0 ? fileName.substring(extensionStartIndex + 1) : "";
    Optional<SpeechToTextEngineOutputFormat> outputFormat =
        SpeechToTextEngineOutputFormat.getSpeechToTextEngineOutputFormatByFileSuffix(fileSuffix);
    if (outputFormat.isEmpty()) {
      throw new IllegalStateException("Unsupported result file format: " + resultFile);
    }

    return studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngineOutputFormat.valueOf(
        outputFormat.get().name());
  }

  @Override
  public void getEngines(
      com.google.protobuf.Empty request,
      io.grpc.stub.StreamObserver<studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngines>
          responseObserver) {
    logger.info(
            "Received request to provide currently active engines");

    try {
      Collection<studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngines.SpeechToTextEngine>
          speechToTextEngines =
              speechToTextService.getSpeechToTextEngines().stream()
                  .map(
                      currentEngine -> {
                        Collection<
                                studio.nkodev.stt.proto.SpeechToTextService
                                    .SpeechToTextEngineOutputFormat>
                            outputFormats =
                                currentEngine.getAllowedOutputFormats().stream()
                                    .map(
                                        currentOutputFormat ->
                                            studio.nkodev.stt.proto.SpeechToTextService
                                                .SpeechToTextEngineOutputFormat.valueOf(
                                                currentOutputFormat.name()))
                                    .toList();
                        Collection<String> modelIdentifiers =
                            currentEngine.getModels().stream()
                                .map(SpeechToTextEngineModel::getIdentifier)
                                .toList();
                        return studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngines
                            .SpeechToTextEngine.newBuilder()
                            .setEngineIdentifier(currentEngine.getIdentifier())
                            .setEngineName(currentEngine.getEngineName())
                            .addAllModelIdentifiers(modelIdentifiers)
                            .addAllAllowedOutputFormats(outputFormats)
                            .build();
                      })
                  .toList();
      responseObserver.onNext(
          studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextEngines.newBuilder()
              .addAllSpeechToTextEngines(speechToTextEngines)
              .build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.asRuntimeException());
    }
  }

  private static SpeechToTextTaskConfiguration mapTaskConfiguration(
      studio.nkodev.stt.proto.SpeechToTextService.TaskRequest request) {
    SpeechToTextEngineOutputFormat outputFormat =
        SpeechToTextEngineOutputFormat.valueOf(request.getOutputFormat().name());
    Locale locale = request.hasLocale() ? Locale.forLanguageTag(request.getLocale()) : null;

    return new SpeechToTextTaskConfiguration(
        request.getEngineIdentifier(),
        locale,
        request.getModelIdentifier(),
        outputFormat);
  }

  private static studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState mapTaskState(
      SpeechToTextTaskState taskState) {
    return switch (taskState) {
      case WAITING_FOR_AUDIO ->
          studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState.WAITING_FOR_AUDIO;
      case PENDING -> studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState.PENDING;
      case RUNNING -> studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState.RUNNING;
      case COMPLETED -> studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState.DONE;
      case FAILED -> studio.nkodev.stt.proto.SpeechToTextService.SpeechToTextTaskState.FAILED;
    };
  }
}
