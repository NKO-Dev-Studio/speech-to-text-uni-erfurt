package studio.nkodev.stt.engine.api;


import java.util.Optional;

/**
 * This enum describes the different output formats which can be produced by a {@link SpeechToTextEngine}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public enum SpeechToTextEngineOutputFormat {
  TXT("txt"),
  VTT("vtt"),
  SRT("srt"),
  TSV("tsv"),
  JSON("json");

  private final String fileSuffix;

  SpeechToTextEngineOutputFormat(String fileSuffix) {
    this.fileSuffix = fileSuffix;
  }

  public String getFileSuffix() {
    return fileSuffix;
  }

  /**
   * @param fileSuffix of the {@link SpeechToTextEngineOutputFormat} which should be returned by this method
   * @return the {@link SpeechToTextEngineOutputFormat} related to the passed fileSuffix
   */
  public static Optional<SpeechToTextEngineOutputFormat> getSpeechToTextEngineOutputFormatByFileSuffix(String fileSuffix) {
    for (SpeechToTextEngineOutputFormat speechToTextEngineOutputFormat : values()) {
      if (speechToTextEngineOutputFormat.getFileSuffix().equals(fileSuffix)) {
        return Optional.of(speechToTextEngineOutputFormat);
      }
    }

    return Optional.empty();
  }
}
