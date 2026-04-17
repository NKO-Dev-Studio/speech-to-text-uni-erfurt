package studio.nkodev.stt.engine.api;

/**
 * This enum describes the different {@link SpeechToTextEngine}s which can be used
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.03.26
 */
public enum SpeechToTextEngineType {
  WHISPER_LOCAL("Whisper (on premise)"),
  OPENAI_API("OpenAI API");

  private final String engineTypeName;

  SpeechToTextEngineType(String engineTypeName) {
    this.engineTypeName = engineTypeName;
  }

  public String getEngineTypeName() {
    return engineTypeName;
  }
}
