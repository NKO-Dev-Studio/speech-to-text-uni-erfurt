package studio.nkodev.stt.client.api;

/**
 * Output formats of speech to text results
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
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
}
