package studio.nkodev.stt.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.SpeechToTextEngineRegistry;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;
import studio.nkodev.stt.task.api.SpeechToTextTaskSchedulerConfiguration;

/**
 * Manages the execution of {@link SpeechToTextTask}s. It is creating the {@link
 * SpeechToTextTaskExecutor}s and submit their execution to the managed {@link
 * java.util.concurrent.ExecutorService}. <br>
 * All {@link SpeechToTextTask}s are executed in an async manner. Even when a client using a
 * synchronous communication way, the thread will create an async task and blocks until the results
 * are ready to consume.
 *
 * @author Nico Kotlenga
 * @since 26.02.26
 */
public class SpeechToTextTaskScheduler {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTaskScheduler.class);
  private static final AtomicInteger EXECUTOR_THREAD_COUNTER = new AtomicInteger(0);
  private static final AtomicInteger SCHEDULER_THREAD_COUNTER = new AtomicInteger(0);
  private static final long NEXT_TASK_POLL_INTERVAL_MS = 2000L;

  private final ExecutorService schedulerPool;
  private final ExecutorService executorPool;
  private final SpeechToTextEngineRegistry engineRegistry;
  private final SpeechToTextTaskStorage speechToTextTaskStorage;
  private final SpeechToTextTaskResultStorage speechToTextTaskResultStorage;
  private final AudioFileStorage audioFileStorage;
  private final Queue<SpeechToTextTask> restoredRunningTasks;
  private volatile boolean running = true;

  public SpeechToTextTaskScheduler(
      SpeechToTextTaskSchedulerConfiguration configuration,
      SpeechToTextEngineRegistry engineRegistry,
      SpeechToTextTaskStorage speechToTextTaskStorage,
      SpeechToTextTaskResultStorage speechToTextTaskResultStorage,
      AudioFileStorage audioFileStorage) {
    logger.info(
        "Initializing SpeechToTextTaskScheduler with a maximum number of {} parallel tasks",
        configuration.getNumberOfParallelTasks());

    this.engineRegistry = engineRegistry;
    this.speechToTextTaskStorage = speechToTextTaskStorage;
    this.speechToTextTaskResultStorage = speechToTextTaskResultStorage;
    this.audioFileStorage = audioFileStorage;
    restoredRunningTasks = new ConcurrentLinkedQueue<>(loadRunningTasksForRecovery());

    schedulerPool =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setDaemon(true);
              thread.setName(
                  "speech-to-text-task-scheduler-thread["
                      + SCHEDULER_THREAD_COUNTER.getAndIncrement()
                      + "]");

              return thread;
            });
    executorPool =
        Executors.newFixedThreadPool(
            configuration.getNumberOfParallelTasks(),
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setDaemon(true);
              thread.setName(
                  "speech-to-text-task-executor-thread["
                      + EXECUTOR_THREAD_COUNTER.getAndIncrement()
                      + "]");

              return thread;
            });

    for (int index = 0; index < configuration.getNumberOfParallelTasks(); index++) {
      executorPool.submit(new ExecutorPoolWorker());
    }
  }

  public void shutdown() {
    logger.info("Shutting down SpeechToTextTaskScheduler");
    running = false;
    schedulerPool.shutdownNow();
    executorPool.shutdownNow();
  }

  private Optional<ScheduledTaskExecution> waitForNextTask() {
    while (running && !Thread.currentThread().isInterrupted()) {
      logger.trace("Looking for next task in task queue");
      try {
        SpeechToTextTask restoredTask = restoredRunningTasks.poll();
        if (restoredTask != null) {
          logger.info(
              "Restoring task {} in state {}", restoredTask.getId(), SpeechToTextTaskState.RUNNING);
          Optional<ScheduledTaskExecution> scheduledTaskExecution =
              createScheduledTaskExecution(restoredTask);
          if (scheduledTaskExecution.isPresent()) {
            return scheduledTaskExecution;
          }

          continue;
        }

        Optional<SpeechToTextTask> claimedTask = speechToTextTaskStorage.claimNextPendingTask();
        if (claimedTask.isPresent()) {
          Optional<ScheduledTaskExecution> scheduledTaskExecution =
              createScheduledTaskExecution(claimedTask.get());
          if (scheduledTaskExecution.isPresent()) {
            return scheduledTaskExecution;
          }
        }
      } catch (Exception exception) {
        logger.error("Error during loading next task", exception);
      }

      sleepUntilNextPollingAttempt();
    }

    return Optional.empty();
  }

  private Collection<SpeechToTextTask> loadRunningTasksForRecovery() {
    logger.info("Loading running tasks for scheduler recovery");
    try (Stream<SpeechToTextTask> runningTasks =
        speechToTextTaskStorage.getTasks(
            SpeechToTextTaskStorage.SpeechToTextTaskFilter.builder()
                .states(Collections.singleton(SpeechToTextTaskState.RUNNING))
                .build())) {
      Collection<SpeechToTextTask> recoveredTasks = new ArrayList<>(runningTasks.toList());
      logger.info("Loaded {} running task(s) for recovery", recoveredTasks.size());
      return recoveredTasks;
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to load running tasks during scheduler startup", exception);
    }
  }

  private Optional<ScheduledTaskExecution> createScheduledTaskExecution(SpeechToTextTask task) {
    try {
      SpeechToTextEngine speechToTextEngine =
          engineRegistry.getEngineByEngineType(task.getConfiguration().engineType());

      Path audioFilePath = audioFileStorage.getAudioFilePathOfTask(task.getId());
      Path resultDirectory = speechToTextTaskResultStorage.createTaskResultDirectory(task.getId());
      return Optional.of(
          new ScheduledTaskExecution(task, speechToTextEngine, audioFilePath, resultDirectory));
    } catch (Exception exception) {
      logger.error("Failed to prepare execution of task {}", task.getId(), exception);
      markTaskAsFailed(task.getId());
      return Optional.empty();
    }
  }

  private void markTaskAsFailed(long taskId) {
    try {
      speechToTextTaskStorage.updateTaskState(taskId, SpeechToTextTaskState.FAILED);
    } catch (Exception exception) {
      logger.error("Failed to mark task {} as failed", taskId, exception);
    }
  }

  private void sleepUntilNextPollingAttempt() {
    try {
      Thread.sleep(NEXT_TASK_POLL_INTERVAL_MS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private final class ExecutorPoolWorker implements Runnable {

    @Override
    public void run() {
      while (running && !Thread.currentThread().isInterrupted()) {
        try {
          Future<Optional<ScheduledTaskExecution>> nextTaskFuture =
              schedulerPool.submit(SpeechToTextTaskScheduler.this::waitForNextTask);
          Optional<ScheduledTaskExecution> scheduledTaskExecution;

          try {
            scheduledTaskExecution = nextTaskFuture.get();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
          } catch (ExecutionException exception) {
            logger.error("Failed to receive next task from scheduler pool", exception);
            continue;
          } catch (CancellationException exception) {
            return;
          }

          if (scheduledTaskExecution.isEmpty()) {
            return;
          }

          ScheduledTaskExecution taskExecution = scheduledTaskExecution.get();
          new SpeechToTextTaskExecutor(
                  taskExecution.task(),
                  taskExecution.speechToTextEngine(),
                  taskExecution.audioFilePath(),
                  taskExecution.resultDirectory(),
                  speechToTextTaskStorage)
              .run();
        } catch (Exception exception) {
          logger.error("Unexpected error in executor worker thread", exception);
        } catch (Error error) {
          logger.error("Error detected. Application restart recommended", error);
        }
      }
    }
  }

  private record ScheduledTaskExecution(
      SpeechToTextTask task,
      SpeechToTextEngine speechToTextEngine,
      Path audioFilePath,
      Path resultDirectory) {}
}
