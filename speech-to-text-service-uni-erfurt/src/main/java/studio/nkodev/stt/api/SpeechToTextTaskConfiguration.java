package studio.nkodev.stt.api;

import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;

import java.util.Locale;

/**
 * Contains the configuration of a {@link SpeechToTextTask}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public record SpeechToTextTaskConfiguration(
    SpeechToTextEngineType engineType,
    Locale locale,
    String modelIdentifier,
    SpeechToTextEngineOutputFormat outputFormat) {

  public SpeechToTextTaskConfiguration {
    requireValue(engineType, "No engine type provided");
    requireValue(modelIdentifier, "No model identifier provided");
    requireValue(outputFormat, "No output format provided");
  }

  private static <T> T requireValue(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
