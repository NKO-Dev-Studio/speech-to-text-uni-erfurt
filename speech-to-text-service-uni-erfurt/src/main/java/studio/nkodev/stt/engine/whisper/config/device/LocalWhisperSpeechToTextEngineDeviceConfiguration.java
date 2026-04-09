package studio.nkodev.stt.engine.whisper.config.device;

/**
 * Interface used by device configurations of the whisper engine
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public interface LocalWhisperSpeechToTextEngineDeviceConfiguration {
  /**
   * @return the description of the device which can be used as CLI argument
   */
  String createDeviceDescription();
}
