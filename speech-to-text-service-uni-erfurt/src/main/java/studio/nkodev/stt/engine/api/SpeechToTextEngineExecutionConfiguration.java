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
    String modelIdentifier,
    Path audioFilePath,
    Path resultDirectoryPath,
    SpeechToTextEngineOutputFormat outputFormat,
    Locale locale) {

  public SpeechToTextEngineExecutionConfiguration {
    requireValue(modelIdentifier, "No model identifier provided");
    requireValue(audioFilePath, "No audio file path provided");
    requireValue(resultDirectoryPath, "No result directory path provided");
    requireValue(outputFormat, "No output format provided");
  }

  public SpeechToTextEngineExecutionConfiguration(
      String modelIdentifier,
      Path audioFilePath,
      Path resultDirectoryPath,
      SpeechToTextEngineOutputFormat outputFormat) {
    this(modelIdentifier, audioFilePath, resultDirectoryPath, outputFormat, null);
  }

  private static <T> T requireValue(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
