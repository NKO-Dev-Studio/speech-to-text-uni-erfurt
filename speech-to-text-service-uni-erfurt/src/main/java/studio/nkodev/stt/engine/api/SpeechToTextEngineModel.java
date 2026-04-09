package studio.nkodev.stt.engine.api;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Representation of models provided by a {@link SpeechToTextEngine}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 26.02.26
 */
public class SpeechToTextEngineModel {

  private final String identifier;
  private final Map<Locale, String> localeOptimizedModelVersionIdentifierByLocale;

  public SpeechToTextEngineModel(String identifier) {
    this(identifier, Collections.emptyMap());
  }

  public SpeechToTextEngineModel(String identifier, Map<Locale, String> localeOptimizedModelVersionIdentifierByLocale) {
    this.identifier = identifier;
    this.localeOptimizedModelVersionIdentifierByLocale = localeOptimizedModelVersionIdentifierByLocale;
  }

  /**
   * @return the identifier of the model
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * @param locale for which an optimized version is searched for
   * @return an {@link Optional} containing the identifier of the optimized version if present
   */
  public Optional<String> getLocaleOptimizedModelVersionIdentifier(Locale locale) {
    return Optional.ofNullable(localeOptimizedModelVersionIdentifierByLocale.get(locale));
  }
}
