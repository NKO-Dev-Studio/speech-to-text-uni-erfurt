package studio.nkodev.stt.engine.api;

import java.nio.file.Path;
import java.util.Locale;

/**
 * This record defines the necessary parameters to execute a speech to text task
 *
 * @param modelIdentifier of the model used for the speech to text task
 * @param audioFilePath of the audio file which will be processed during the speech to text task
 * @param resultDirectoryPath in which the resulting files will be persisted in
 * @param outputFormat in which the result will be written in
 * @param locale hint to improve the transcription results (can be null)
 *
 * @author Nico Kotlenga
 * @since 26.02.2026
 */
public record SpeechToTextEngineExecutionConfiguration(
    long taskId,
    String modelIdentifier,
    Path audioFilePath,
    Path resultDirectoryPath,
    SpeechToTextEngineOutputFormat outputFormat,
    Locale locale) {

  public SpeechToTextEngineExecutionConfiguration {
    if (taskId < 1) {
      throw new IllegalArgumentException("Invalid task id provided");
    }
    requireValue(modelIdentifier, "No model identifier provided");
    requireValue(audioFilePath, "No audio file path provided");
    requireValue(resultDirectoryPath, "No result directory path provided");
    requireValue(outputFormat, "No output format provided");
  }

  public SpeechToTextEngineExecutionConfiguration(
      long taskId,
      String modelIdentifier,
      Path audioFilePath,
      Path resultDirectoryPath,
      SpeechToTextEngineOutputFormat outputFormat) {
    this(taskId, modelIdentifier, audioFilePath, resultDirectoryPath, outputFormat, null);
  }

  public SpeechToTextEngineExecutionConfiguration(
      String modelIdentifier,
      Path audioFilePath,
      Path resultDirectoryPath,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    this(1L, modelIdentifier, audioFilePath, resultDirectoryPath, outputFormat, locale);
  }

  public SpeechToTextEngineExecutionConfiguration(
      String modelIdentifier,
      Path audioFilePath,
      Path resultDirectoryPath,
      SpeechToTextEngineOutputFormat outputFormat) {
    this(1L, modelIdentifier, audioFilePath, resultDirectoryPath, outputFormat, null);
  }

  private static <T> T requireValue(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
