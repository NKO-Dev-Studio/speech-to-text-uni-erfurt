package studio.nkodev.stt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.client.adapter.SpeechToTextServiceAdapter;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.api.SpeechToTextTaskStateConsumer;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;
import studio.nkodev.stt.client.exception.SpeechToTextServiceClientErrorType;
import studio.nkodev.stt.client.exception.SpeechToTextServiceClientException;

class SpeechToTextServiceClientTest {

  @Test
  void shouldObserveOnlyChangedTaskStatesAndStopAfterTerminalState() throws Exception {
    StubSpeechToTextServiceAdapter speechToTextServiceAdapter =
        new StubSpeechToTextServiceAdapter(
            List.of(
                SpeechToTextTaskState.PENDING,
                SpeechToTextTaskState.PENDING,
                SpeechToTextTaskState.RUNNING,
                SpeechToTextTaskState.RUNNING,
                SpeechToTextTaskState.DONE));
    SpeechToTextServiceClientConfiguration configuration =
        SpeechToTextServiceClientConfiguration.builder(
                "localhost",
                8443,
                Files.createTempFile("stt-client-test-server", ".pem"),
                SpeechToTextTransferConfigurationFactory.streaming(),
                SpeechToTextTransferConfigurationFactory.streaming())
            .taskObservationInitialDelayMs(5L)
            .taskObservationMaximumDelayMs(20L)
            .build();
    SpeechToTextServiceClient speechToTextServiceClient =
        new SpeechToTextServiceClient(speechToTextServiceAdapter, configuration);

    List<SpeechToTextTaskState> observedStates = new CopyOnWriteArrayList<>();
    CountDownLatch completionLatch = new CountDownLatch(3);
    try {
      speechToTextServiceClient.observeTask(
          42L,
          (taskId, speechToTextTaskState) -> {
            observedStates.add(speechToTextTaskState);
            completionLatch.countDown();
          });

      completionLatch.await(1, TimeUnit.SECONDS);
    } finally {
      speechToTextServiceClient.close();
    }

    assertEquals(
        List.of(
            SpeechToTextTaskState.PENDING,
            SpeechToTextTaskState.RUNNING,
            SpeechToTextTaskState.DONE),
        observedStates);
  }

  @Test
  void shouldStopObservationAndNotifyConsumerWhenTaskNotFound() throws Exception {
    StubSpeechToTextServiceAdapter speechToTextServiceAdapter =
        new StubSpeechToTextServiceAdapter(
            List.of(SpeechToTextTaskState.PENDING),
            new SpeechToTextServiceClientException(
                SpeechToTextServiceClientErrorType.NOT_FOUND, "Task not found"));
    SpeechToTextServiceClient speechToTextServiceClient =
        new SpeechToTextServiceClient(speechToTextServiceAdapter, observationConfiguration());

    List<SpeechToTextTaskState> observedStates = new CopyOnWriteArrayList<>();
    AtomicReference<SpeechToTextServiceClientErrorType> observedErrorType = new AtomicReference<>();
    AtomicReference<Boolean> observedTerminal = new AtomicReference<>();
    AtomicReference<Long> observedErrorTaskId = new AtomicReference<>();
    AtomicInteger errorCallbackCount = new AtomicInteger();
    CountDownLatch errorLatch = new CountDownLatch(1);
    try {
      speechToTextServiceClient.observeTask(
          42L,
          new SpeechToTextTaskStateConsumer() {
            @Override
            public void onTaskStateChanged(
                long taskId, SpeechToTextTaskState speechToTextTaskState) {
              observedStates.add(speechToTextTaskState);
            }

            @Override
            public void onObservationError(
                long taskId,
                SpeechToTextServiceClientErrorType errorType,
                boolean terminal) {
              observedErrorTaskId.set(taskId);
              observedErrorType.set(errorType);
              observedTerminal.set(terminal);
              errorCallbackCount.incrementAndGet();
              errorLatch.countDown();
            }
          });

      assertTrue(errorLatch.await(1, TimeUnit.SECONDS), "Expected onObservationError to be called");
      int invocationsAtError = speechToTextServiceAdapter.getStateInvocations();

      // Allow time for any (erroneous) further polling to occur after the terminal error.
      Thread.sleep(150L);

      assertEquals(42L, observedErrorTaskId.get());
      assertEquals(SpeechToTextServiceClientErrorType.NOT_FOUND, observedErrorType.get());
      assertEquals(Boolean.TRUE, observedTerminal.get());
      assertEquals(1, errorCallbackCount.get());
      assertEquals(List.of(SpeechToTextTaskState.PENDING), observedStates);
      assertEquals(
          invocationsAtError,
          speechToTextServiceAdapter.getStateInvocations(),
          "Observation must stop polling after a NOT_FOUND error");
    } finally {
      speechToTextServiceClient.close();
    }
  }

  @Test
  void shouldKeepObservingAndReportNonTerminalErrors() throws Exception {
    StubSpeechToTextServiceAdapter speechToTextServiceAdapter =
        new StubSpeechToTextServiceAdapter(
            List.of(SpeechToTextTaskState.PENDING),
            new SpeechToTextServiceClientException(
                SpeechToTextServiceClientErrorType.CONNECTION_ERROR, "Service unavailable"));
    SpeechToTextServiceClient speechToTextServiceClient =
        new SpeechToTextServiceClient(speechToTextServiceAdapter, observationConfiguration());

    AtomicReference<SpeechToTextServiceClientErrorType> observedErrorType = new AtomicReference<>();
    AtomicReference<Boolean> observedTerminal = new AtomicReference<>();
    CountDownLatch errorLatch = new CountDownLatch(2);
    try {
      speechToTextServiceClient.observeTask(
          42L,
          new SpeechToTextTaskStateConsumer() {
            @Override
            public void onTaskStateChanged(
                long taskId, SpeechToTextTaskState speechToTextTaskState) {}

            @Override
            public void onObservationError(
                long taskId,
                SpeechToTextServiceClientErrorType errorType,
                boolean terminal) {
              observedErrorType.set(errorType);
              observedTerminal.set(terminal);
              errorLatch.countDown();
            }
          });

      assertTrue(
          errorLatch.await(1, TimeUnit.SECONDS),
          "Expected the observation to keep reporting non-terminal errors");
      assertEquals(SpeechToTextServiceClientErrorType.CONNECTION_ERROR, observedErrorType.get());
      assertEquals(Boolean.FALSE, observedTerminal.get());
    } finally {
      speechToTextServiceClient.close();
    }
  }

  private static SpeechToTextServiceClientConfiguration observationConfiguration() throws Exception {
    return SpeechToTextServiceClientConfiguration.builder(
            "localhost",
            8443,
            Files.createTempFile("stt-client-test-server", ".pem"),
            SpeechToTextTransferConfigurationFactory.streaming(),
            SpeechToTextTransferConfigurationFactory.streaming())
        .taskObservationInitialDelayMs(5L)
        .taskObservationMaximumDelayMs(20L)
        .build();
  }
}

final class StubSpeechToTextServiceAdapter implements SpeechToTextServiceAdapter {

  private final List<SpeechToTextTaskState> states;
  private final SpeechToTextServiceClientException errorAfterStates;
  private final AtomicInteger stateInvocations = new AtomicInteger();
  private int stateIndex;

  StubSpeechToTextServiceAdapter(List<SpeechToTextTaskState> states) {
    this(states, null);
  }

  StubSpeechToTextServiceAdapter(
      List<SpeechToTextTaskState> states, SpeechToTextServiceClientException errorAfterStates) {
    this.states = List.copyOf(states);
    this.errorAfterStates = errorAfterStates;
  }

  int getStateInvocations() {
    return stateInvocations.get();
  }

  @Override
  public long startSpeechToTextTask(
      Path audioFile,
      String engine,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    return 0L;
  }

  @Override
  public synchronized SpeechToTextTaskState getStateOfTask(long taskId) {
    stateInvocations.incrementAndGet();
    if (stateIndex >= states.size() && errorAfterStates != null) {
      throw errorAfterStates;
    }
    SpeechToTextTaskState state =
        states.get(Math.min(stateIndex, Math.max(states.size() - 1, 0)));
    stateIndex++;
    return state;
  }

  @Override
  public void saveResultsOfTask(long taskId, Path resultDirectory) {

  }

  @Override
  public Collection<SpeechToTextEngine> getAvailableEngines() {
    return List.of();
  }

  @Override
  public void close() {}
}
