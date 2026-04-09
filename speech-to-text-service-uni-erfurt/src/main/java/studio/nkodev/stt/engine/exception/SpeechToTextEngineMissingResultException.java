package studio.nkodev.stt.engine.exception;

import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;

import java.nio.file.Path;
import java.text.MessageFormat;

/**
 * This exception is thrown when a result in a specified format is missing
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class SpeechToTextEngineMissingResultException extends SpeechToTextEngineException {
  private final SpeechToTextEngineOutputFormat outputFormat;
  private final Path audioFilePath;

  public SpeechToTextEngineMissingResultException(SpeechToTextEngineOutputFormat outputFormat, Path audioFilePath) {
    this.outputFormat = outputFormat;
    this.audioFilePath = audioFilePath;

    super(MessageFormat.format("Expected result in format {0} for audio file located at {1} is missing", outputFormat, audioFilePath));
  }

  public SpeechToTextEngineOutputFormat getOutputFormat() {
    return outputFormat;
  }

  public Path getAudioFilePath() {
    return audioFilePath;
  }
}
