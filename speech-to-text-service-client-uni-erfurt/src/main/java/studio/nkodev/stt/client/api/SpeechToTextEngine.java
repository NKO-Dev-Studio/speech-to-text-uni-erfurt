package studio.nkodev.stt.client.api;

import java.util.Collection;

/**
 * Representation of the different engines provided by the current connected speech-to-text-service
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public record SpeechToTextEngine(
    String engineIdentifier,
    String engineName,
    Collection<String> modelIdentifiers,
    Collection<SpeechToTextEngineOutputFormat> allowedOutputFormats) {}
