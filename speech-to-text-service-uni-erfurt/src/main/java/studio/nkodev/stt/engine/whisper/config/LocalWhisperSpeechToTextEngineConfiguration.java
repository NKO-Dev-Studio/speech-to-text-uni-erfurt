package studio.nkodev.stt.engine.whisper.config;

import studio.nkodev.stt.engine.whisper.LocalWhisperSpeechToTextEngine;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineDeviceConfiguration;

import java.nio.file.Path;

/**
 * Interface definiting the configuration of the {@link LocalWhisperSpeechToTextEngine}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public interface LocalWhisperSpeechToTextEngineConfiguration {
  /**
   * @return the path of the whisper executable used to execute speech to text tasks
   */
  Path getWhisperExecutable();

  /**
   * @return the configuration of the devices used to perform the speech to text tasks
   */
  LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration();
}
