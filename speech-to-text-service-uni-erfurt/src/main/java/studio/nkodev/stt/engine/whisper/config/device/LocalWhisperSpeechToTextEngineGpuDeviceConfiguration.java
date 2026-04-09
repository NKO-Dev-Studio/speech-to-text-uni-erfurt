package studio.nkodev.stt.engine.whisper.config.device;

import java.text.MessageFormat;

/**
 * Configuration using the local GPU as device
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class LocalWhisperSpeechToTextEngineGpuDeviceConfiguration
    implements LocalWhisperSpeechToTextEngineDeviceConfiguration {

  private final Integer gpuNumber;

  public LocalWhisperSpeechToTextEngineGpuDeviceConfiguration() {
    gpuNumber = null;
  }

  public LocalWhisperSpeechToTextEngineGpuDeviceConfiguration(Integer gpuNumber) {
    this.gpuNumber = gpuNumber;
  }

  @Override
  public String createDeviceDescription() {
    if (gpuNumber == null) {
      return "gpu";
    } else {
      return MessageFormat.format("gpu:{0}", gpuNumber);
    }
  }
}
