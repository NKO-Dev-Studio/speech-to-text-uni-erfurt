package studio.nkodev.stt.engine.exception;

import studio.nkodev.stt.engine.api.SpeechToTextEngine;

import java.text.MessageFormat;

/**
 * This exception will be thrown when try to use a model which is not provided by the current active
 * {@link SpeechToTextEngine}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class SpeechToTextEngineModelNotFoundException extends SpeechToTextEngineException {

  private final String identifier;

  public SpeechToTextEngineModelNotFoundException(String identifier) {
    super(
        MessageFormat.format("Not found SpeechToTextEngineModel with identifier: {0}", identifier));
    this.identifier = identifier;
  }

  public String getIdentifier() {
    return identifier;
  }
}
