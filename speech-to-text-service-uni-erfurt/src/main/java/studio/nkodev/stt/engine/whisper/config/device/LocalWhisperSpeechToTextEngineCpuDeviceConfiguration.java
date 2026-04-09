package studio.nkodev.stt.engine.whisper.config.device;

/**
 * Configuration using the local CPU as device
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class LocalWhisperSpeechToTextEngineCpuDeviceConfiguration implements LocalWhisperSpeechToTextEngineDeviceConfiguration {

  private final int numberOfThreads;

  public LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(int numberOfThreads) {
    this.numberOfThreads = numberOfThreads;
  }

  @Override
  public String createDeviceDescription() {
    return "cpu";
  }
}
