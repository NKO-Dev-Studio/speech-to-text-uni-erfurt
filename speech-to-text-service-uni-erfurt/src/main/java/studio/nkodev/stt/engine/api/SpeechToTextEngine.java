package studio.nkodev.stt.engine.api;

import studio.nkodev.stt.engine.exception.SpeechToTextEngineExecutionException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineMissingResultException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineModelNotFoundException;

import java.util.Collection;
import java.util.Optional;

/**
 * Defines the capabilities of a speech to text engine
 *
 * @author Nico Kotlenga
 * @since 26.02.26
 */
public interface SpeechToTextEngine {

  /**
   * @return a {@link Collection} containing all {@link SpeechToTextEngineModel}s provided by the
   *     {@link SpeechToTextEngine}
   */
  Collection<SpeechToTextEngineModel> getModels();

  /**
   * @param identifier of the required {@link SpeechToTextEngineModel}
   * @return an {@link Optional} containing the specified {@link SpeechToTextEngineModel} if present
   */
  Optional<SpeechToTextEngineModel> getModelByIdentifier(String identifier);

  /**
   * Executes a speech to text task using the provided configuration
   *
   * @param speechToTextEngineExecutionConfiguration defining the configuration used to execute the
   *     task
   * @throws SpeechToTextEngineModelNotFoundException when the defined model is not provided by the
   *     current active engine
   * @throws SpeechToTextEngineExecutionException when an error occurred during performing the
   *     speech to text task
   * @throws IllegalArgumentException when any argument not matching the expectations (e.g. passing
   *     an empty list of output formats)
   */
  void executeSpeechToTextTask(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration)
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          IllegalArgumentException,
          SpeechToTextEngineMissingResultException;

  /**
   * @return the identifier of this engine instance
   */
  String getIdentifier();

  /**
   * @return the user-facing engine name of this instance
   */
  String getEngineName();

  /**
   * @return the {@link SpeechToTextEngineType} of this instance
   */
  SpeechToTextEngineType getSpeechToTextEngineType();

  /**
   * @return the {@link SpeechToTextEngineOutputFormat}s which can be produced by this engine
   */
  Collection<SpeechToTextEngineOutputFormat> getAllowedOutputFormats();
}
