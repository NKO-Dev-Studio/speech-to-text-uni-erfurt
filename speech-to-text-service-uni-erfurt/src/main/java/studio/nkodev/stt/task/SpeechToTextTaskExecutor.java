package studio.nkodev.stt.task;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineExecutionConfiguration;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;

/**
 * This component is responsible to execute a single speech to text task.
 *
 * @author Nico Kotlenga
 * @since 26.02.26
 */
public class SpeechToTextTaskExecutor implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTaskExecutor.class);

  private final Path audioFilePath;
  private final Path resultDirectory;
  private final SpeechToTextTask speechToTextTask;
  private final SpeechToTextEngine speechToTextEngine;
  private final SpeechToTextTaskStorage speechToTextTaskStorage;

  public SpeechToTextTaskExecutor(
      SpeechToTextTask speechToTextTask,
      SpeechToTextEngine speechToTextEngine,
      Path audioFilePath,
      Path resultDirectory,
      SpeechToTextTaskStorage speechToTextTaskStorage) {
    this.audioFilePath = audioFilePath;
    this.resultDirectory = resultDirectory;
    this.speechToTextTask = speechToTextTask;
    this.speechToTextEngine = speechToTextEngine;
    this.speechToTextTaskStorage = speechToTextTaskStorage;
  }

  /** Executes the speech to text task */
  @Override
  public void run() {
    logger.info("Starting execution of task {}", speechToTextTask.getId());

    SpeechToTextEngineExecutionConfiguration engineConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            speechToTextTask.getId(),
            speechToTextTask.getConfiguration().modelIdentifier(),
            audioFilePath,
            resultDirectory,
            speechToTextTask.getConfiguration().outputFormat(),
            speechToTextTask.getConfiguration().locale());
    try {
      speechToTextEngine.executeSpeechToTextTask(engineConfiguration);
      speechToTextTaskStorage.updateTaskState(
          speechToTextTask.getId(), SpeechToTextTaskState.COMPLETED);
    } catch (Exception e) {
      logger.error("Error during execution of task {}", speechToTextTask.getId(), e);
      try {
        speechToTextTaskStorage.updateTaskState(
            speechToTextTask.getId(), SpeechToTextTaskState.FAILED);
      } catch (Exception ex) {
        logger.error(
            "Error during setting task state of task {} to failed", speechToTextTask.getId(), ex);
      }
    }
  }
}
