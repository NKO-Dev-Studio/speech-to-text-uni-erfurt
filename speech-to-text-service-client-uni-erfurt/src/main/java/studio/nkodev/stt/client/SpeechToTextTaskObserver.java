package studio.nkodev.stt.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.client.adapter.SpeechToTextServiceAdapter;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.api.SpeechToTextTaskStateConsumer;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;

/**
 * Handles polling-based observation of speech-to-text task state changes.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
final class SpeechToTextTaskObserver implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTaskObserver.class);

  private final SpeechToTextServiceAdapter speechToTextServiceAdapter;
  private final SpeechToTextServiceClientConfiguration configuration;
  private final Map<Long, SpeechToTextTaskObservation> taskObservations = new ConcurrentHashMap<>();

  private ScheduledExecutorService observationExecutorService;
  private volatile boolean closed;

  SpeechToTextTaskObserver(
      SpeechToTextServiceAdapter speechToTextServiceAdapter,
      SpeechToTextServiceClientConfiguration configuration) {
    this.speechToTextServiceAdapter =
        Objects.requireNonNull(
            speechToTextServiceAdapter, "No speech-to-text-service adapter provided");
    this.configuration = Objects.requireNonNull(configuration, "No client configuration provided");
  }

  void observeTask(long taskId, SpeechToTextTaskStateConsumer taskStateConsumer) {
    verifyObserverNotClosed();
    Objects.requireNonNull(taskStateConsumer, "No task state consumer provided");

    SpeechToTextTaskObservation taskObservation =
        new SpeechToTextTaskObservation(
            taskId, taskStateConsumer, configuration.taskObservationInitialDelayMs());
    SpeechToTextTaskObservation previousTaskObservation =
        taskObservations.put(taskId, taskObservation);
    if (previousTaskObservation != null) {
      previousTaskObservation.stop();
    }

    initializeObservationExecutorIfRequired();
    scheduleObservation(taskObservation, 0L);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }

    closed = true;
    taskObservations.values().forEach(SpeechToTextTaskObservation::stop);
    taskObservations.clear();

    if (observationExecutorService != null) {
      observationExecutorService.shutdownNow();
    }
  }

  private void pollTaskState(SpeechToTextTaskObservation taskObservation) {
    if (closed || !taskObservation.isActive()) {
      return;
    }

    try {
      SpeechToTextTaskState currentTaskState =
          speechToTextServiceAdapter.getStateOfTask(taskObservation.getTaskId());
      boolean changed = taskObservation.registerState(currentTaskState);
      if (changed) {
        taskObservation.getTaskStateConsumer()
            .onTaskStateChanged(taskObservation.getTaskId(), currentTaskState);
      }

      if (currentTaskState.isTerminal()) {
        taskObservation.stop();
        taskObservations.remove(taskObservation.getTaskId());
        return;
      }

      long nextDelay =
          changed
              ? configuration.taskObservationInitialDelayMs()
              : taskObservation.increaseDelay(
                  configuration.taskObservationDelayMultiplier(),
                  configuration.taskObservationMaximumDelayMs());
      scheduleObservation(taskObservation, nextDelay);
    } catch (Exception exception) {
      if (closed || !taskObservation.isActive()) {
        return;
      }

      logger.warn("Failed to observe task {}", taskObservation.getTaskId(), exception);
      scheduleObservation(taskObservation, configuration.taskObservationMaximumDelayMs());
    }
  }

  private synchronized void initializeObservationExecutorIfRequired() {
    if (observationExecutorService != null) {
      return;
    }

    logger.debug("Initializing scheduled executor for task observation");
    observationExecutorService =
        Executors.newScheduledThreadPool(configuration.observationThreadPoolSize());
  }

  private void scheduleObservation(SpeechToTextTaskObservation taskObservation, long delayMs) {
    observationExecutorService.schedule(
        () -> pollTaskState(taskObservation), delayMs, TimeUnit.MILLISECONDS);
  }

  private void verifyObserverNotClosed() {
    if (closed) {
      throw new IllegalStateException("SpeechToTextTaskObserver already closed");
    }
  }
}
