package studio.nkodev.stt.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
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

  private final Map<String, SpeechToTextEngine> enginesByIdentifier = new LinkedHashMap<>();

  public void registerSpeechToTextEngine(SpeechToTextEngine speechToTextEngine) {
    String engineIdentifier = speechToTextEngine.getIdentifier();
    if (enginesByIdentifier.containsKey(engineIdentifier)) {
      throw new IllegalArgumentException(
          "Engine identifier " + engineIdentifier + " already registered");
    }
    enginesByIdentifier.put(engineIdentifier, speechToTextEngine);
  }

  public Collection<SpeechToTextEngine> getEngines() {
    return enginesByIdentifier.values();
  }

  public SpeechToTextEngine getEngineByIdentifier(String engineIdentifier)
      throws SpeechToTextEngineException {
    if (!enginesByIdentifier.containsKey(engineIdentifier)) {
      throw new SpeechToTextEngineException("Engine identifier " + engineIdentifier + " not registered");
    }

    return enginesByIdentifier.get(engineIdentifier);
  }

  public SpeechToTextEngine getEngineByEngineType(SpeechToTextEngineType speechToTextEngineType)
      throws SpeechToTextEngineException {
    return getEngineByIdentifier(speechToTextEngineType.name());
  }
}
