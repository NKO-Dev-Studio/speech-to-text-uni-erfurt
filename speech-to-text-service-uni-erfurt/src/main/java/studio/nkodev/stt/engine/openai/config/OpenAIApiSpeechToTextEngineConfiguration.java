package studio.nkodev.stt.engine.openai.config;

import java.net.URI;

/**
 * Configuration used by {@link studio.nkodev.stt.engine.openai.OpenAIApiEngine}.
 *
 * @author Nico Kotlenga
 * @since 17.04.26
 */
public interface OpenAIApiSpeechToTextEngineConfiguration {

  String getIdentifier();

  URI getApiBaseUrl();

  String getApiToken();
}
