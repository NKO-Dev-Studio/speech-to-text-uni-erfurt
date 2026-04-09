package studio.nkodev.stt.client;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import studio.nkodev.stt.client.adapter.SpeechToTextServiceAdapter;
import studio.nkodev.stt.client.adapter.grpc.GrpcSpeechToTextServiceAdapter;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.api.SpeechToTextTaskStateConsumer;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;

/**
 * Client used to send requests to the speech-to-text-service and consumes the response.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public class SpeechToTextServiceClient implements AutoCloseable {

  private final SpeechToTextServiceAdapter speechToTextServiceAdapter;
  private final SpeechToTextTaskObserver speechToTextTaskObserver;
  private volatile boolean closed;

  public SpeechToTextServiceClient(SpeechToTextServiceClientConfiguration configuration) {
    this(createSpeechToTextServiceAdapter(configuration), configuration);
  }

  SpeechToTextServiceClient(
      SpeechToTextServiceAdapter speechToTextServiceAdapter,
      SpeechToTextServiceClientConfiguration configuration) {
    this(
        speechToTextServiceAdapter,
        new SpeechToTextTaskObserver(
            speechToTextServiceAdapter,
            Objects.requireNonNull(configuration, "No client configuration provided")));
  }

  SpeechToTextServiceClient(
      SpeechToTextServiceAdapter speechToTextServiceAdapter,
      SpeechToTextTaskObserver speechToTextTaskObserver) {
    this.speechToTextServiceAdapter =
        Objects.requireNonNull(
            speechToTextServiceAdapter, "No speech-to-text-service adapter provided");
    this.speechToTextTaskObserver =
        Objects.requireNonNull(speechToTextTaskObserver, "No speech-to-text task observer provided");
  }

  /**
   * Starts a speech to text task using the provided audio file and configuration.
   *
   * @param audioFile for which a speech to text task will be triggered
   * @param engineIdentifier used to perform the speech to text task
   * @param modelIdentifier of the engine model
   * @param outputFormat which will be produced
   * @return the id of the created speech to text task
   */
  public long startSpeechToTextTask(
      Path audioFile,
      String engineIdentifier,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat) {
    return startSpeechToTextTask(audioFile, engineIdentifier, modelIdentifier, outputFormat, null);
  }

  /**
   * Starts observing of task state changes of the specified task.
   *
   * @param taskId of the task to observe
   * @param taskStateConsumer triggered when a changed state has been detected
   */
  public void observeTask(long taskId, SpeechToTextTaskStateConsumer taskStateConsumer) {
    verifyClientNotClosed();
    speechToTextTaskObserver.observeTask(taskId, taskStateConsumer);
  }

  /**
   * Starts a speech to text task using the provided audio file and configuration.
   *
   * @param audioFile for which a speech to text task will be triggered
   * @param engineIdentifier used to perform the speech to text task
   * @param modelIdentifier of the engine model
   * @param outputFormat which will be produced
   * @param locale of the audio file (definition can improve the results because the engine has the
   *     possibility to use a language optimized model)
   * @return the id of the created speech to text task
   */
  public long startSpeechToTextTask(
      Path audioFile,
      String engineIdentifier,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    verifyClientNotClosed();
    return speechToTextServiceAdapter.startSpeechToTextTask(
        audioFile, engineIdentifier, modelIdentifier, outputFormat, locale);
  }

  /**
   * Loads the current state of the specified task.
   *
   * @param taskId of the task which state should be loaded
   * @return the {@link SpeechToTextTaskState} of the specified task
   */
  public SpeechToTextTaskState getStateOfTask(long taskId) {
    verifyClientNotClosed();
    return speechToTextServiceAdapter.getStateOfTask(taskId);
  }

  /**
   * Persists the results of a task into the specified file. <br>
   * <strong>Important:</strong> Please keep in mind that consuming results is only allowed once.
   * Results persisted in speech-to-text-service are deleted after consuming.
   *
   * @param taskId which results will be persisted
   * @param resultPath to which the result file will be written in
   * @return the persisted result file path
   */
  public void saveResultsOfTask(long taskId, Path resultPath) {
    verifyClientNotClosed();
    speechToTextServiceAdapter.saveResultsOfTask(taskId, resultPath);
  }

  /**
   * @return the currently available engines
   */
  public Collection<SpeechToTextEngine> getAvailableEngines() {
    verifyClientNotClosed();
    Collection<SpeechToTextEngine> availableEngines = speechToTextServiceAdapter.getAvailableEngines();
    return availableEngines == null ? Collections.emptyList() : availableEngines;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }

    closed = true;
    speechToTextTaskObserver.close();
    speechToTextServiceAdapter.close();
  }

  private void verifyClientNotClosed() {
    if (closed) {
      throw new IllegalStateException("SpeechToTextServiceClient already closed");
    }
  }

  private static SpeechToTextServiceAdapter createSpeechToTextServiceAdapter(
      SpeechToTextServiceClientConfiguration configuration) {
    return new GrpcSpeechToTextServiceAdapter(configuration);
  }
}
