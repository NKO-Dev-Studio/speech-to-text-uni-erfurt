package studio.nkodev.stt.engine;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineException;

/**
 * Registry of all currently active {@link studio.nkodev.stt.engine.api.SpeechToTextEngine}s
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 04.04.26
 */
public class SpeechToTextEngineRegistry {

  private final Map<SpeechToTextEngineType, SpeechToTextEngine> enginesByEngineType =
      new EnumMap<>(SpeechToTextEngineType.class);

  public void registerSpeechToTextEngine(SpeechToTextEngine speechToTextEngine) {
    enginesByEngineType.put(speechToTextEngine.getSpeechToTextEngineType(), speechToTextEngine);
  }

  public Collection<SpeechToTextEngine> getEngines() {
    return enginesByEngineType.values();
  }

  public SpeechToTextEngine getEngineByEngineType(SpeechToTextEngineType speechToTextEngineType)
      throws SpeechToTextEngineException {
    if (!enginesByEngineType.containsKey(speechToTextEngineType)) {
      throw new SpeechToTextEngineException(
          "Engine type " + speechToTextEngineType + " not registered");
    }

    return enginesByEngineType.get(speechToTextEngineType);
  }
}
