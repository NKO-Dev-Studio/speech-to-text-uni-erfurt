package studio.nkodev.stt.engine.whisper.config.device;

/**
 * Configuration using the apple metal improvement for engine execution
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class LocalWhisperSpeechToTextEngineMpsDevice implements LocalWhisperSpeechToTextEngineDeviceConfiguration {
  @Override
  public String createDeviceDescription() {
    return "mps";
  }
}
