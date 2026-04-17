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
    String engineIdentifier,
    Locale locale,
    String modelIdentifier,
    SpeechToTextEngineOutputFormat outputFormat) {

  public SpeechToTextTaskConfiguration(
      SpeechToTextEngineType engineType,
      Locale locale,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat) {
    this(engineType.name(), locale, modelIdentifier, outputFormat);
  }

  public SpeechToTextTaskConfiguration {
    requireStringValue(engineIdentifier, "No engine identifier provided");
    requireStringValue(modelIdentifier, "No model identifier provided");
    requireValue(outputFormat, "No output format provided");
  }

  private static String requireStringValue(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static <T> T requireValue(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
