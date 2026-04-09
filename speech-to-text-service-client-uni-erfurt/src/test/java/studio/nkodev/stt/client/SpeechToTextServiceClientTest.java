package studio.nkodev.stt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.client.adapter.SpeechToTextServiceAdapter;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;

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
}

final class StubSpeechToTextServiceAdapter implements SpeechToTextServiceAdapter {

  private final List<SpeechToTextTaskState> states;
  private int stateIndex;

  StubSpeechToTextServiceAdapter(List<SpeechToTextTaskState> states) {
    this.states = List.copyOf(states);
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
