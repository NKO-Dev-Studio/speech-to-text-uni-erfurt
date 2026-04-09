package studio.nkodev.stt.engine.whisper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.engine.api.SpeechToTextEngine;
import studio.nkodev.stt.engine.api.SpeechToTextEngineExecutionConfiguration;
import studio.nkodev.stt.engine.api.SpeechToTextEngineModel;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineExecutionException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineMissingResultException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineModelNotFoundException;
import studio.nkodev.stt.engine.whisper.config.LocalWhisperSpeechToTextEngineConfiguration;

/**
 * This {@link SpeechToTextEngine} is using a local whisper instance to perform the speech to text
 * tasks.
 *
 * @author Nico Kotlenga
 * @since 26.02.26
 */
public class LocalWhisperSpeechToTextEngine implements SpeechToTextEngine {

  private static final Logger logger =
      LoggerFactory.getLogger(LocalWhisperSpeechToTextEngine.class);

  private static final Set<SpeechToTextEngineModel> WHISPER_MODELS =
      Set.of(
          new SpeechToTextEngineModel("tiny", Map.of(Locale.ENGLISH, "tiny.en")),
          new SpeechToTextEngineModel("base", Map.of(Locale.ENGLISH, "base.en")),
          new SpeechToTextEngineModel("small", Map.of(Locale.ENGLISH, "small.en")),
          new SpeechToTextEngineModel("medium", Map.of(Locale.ENGLISH, "medium.en")),
          new SpeechToTextEngineModel("large"),
          new SpeechToTextEngineModel("turbo"));
  private static final Map<String, SpeechToTextEngineModel> WHISPER_MODELS_BY_IDENTIFIER =
      WHISPER_MODELS.stream()
          .collect(Collectors.toMap(SpeechToTextEngineModel::getIdentifier, model -> model));
  private static final Set<SpeechToTextEngineOutputFormat> ALLOWED_OUTPUT_FORMATS =
      Set.of(
          SpeechToTextEngineOutputFormat.TXT,
          SpeechToTextEngineOutputFormat.SRT,
          SpeechToTextEngineOutputFormat.JSON,
          SpeechToTextEngineOutputFormat.TSV,
          SpeechToTextEngineOutputFormat.VTT);

  private final LocalWhisperSpeechToTextEngineConfiguration configuration;

  public LocalWhisperSpeechToTextEngine(LocalWhisperSpeechToTextEngineConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Collection<SpeechToTextEngineModel> getModels() {
    return WHISPER_MODELS;
  }

  @Override
  public Optional<SpeechToTextEngineModel> getModelByIdentifier(String identifier) {
    return Optional.ofNullable(WHISPER_MODELS_BY_IDENTIFIER.get(identifier));
  }

  @Override
  public void executeSpeechToTextTask(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration)
      throws SpeechToTextEngineExecutionException,
          SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineMissingResultException {
    logger.info(
        "Starting speech to text task for file {}",
        speechToTextEngineExecutionConfiguration.audioFilePath());
    String[] commandLineArray = createCommandLineArray(speechToTextEngineExecutionConfiguration);
    Path expectedResultFilePath =
        createExpectedResultFilePath(speechToTextEngineExecutionConfiguration);

    Runtime runtime = Runtime.getRuntime();

    try {
      logger.debug(
          "Start engine processing for file {} using command {}",
          speechToTextEngineExecutionConfiguration.audioFilePath(),
          Arrays.toString(commandLineArray));
      Process process = runtime.exec(commandLineArray);
      int processExitCode = process.waitFor();
      logger.debug(
          "Finished engine processing for file {}",
          speechToTextEngineExecutionConfiguration.audioFilePath());
      if (processExitCode != 0) {
        String errorMessage =
            new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        throw new SpeechToTextEngineExecutionException(errorMessage);
      }

      if (!Files.exists(expectedResultFilePath)) {
        String errorMessage =
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        logger.error(errorMessage);
        throw new SpeechToTextEngineMissingResultException(
            speechToTextEngineExecutionConfiguration.outputFormat(),
            speechToTextEngineExecutionConfiguration.audioFilePath());
      }
    } catch (Exception e) {
      if (e instanceof SpeechToTextEngineMissingResultException) {
        throw (SpeechToTextEngineMissingResultException) e;
      }
      throw new SpeechToTextEngineExecutionException(
          "Error while executing speech to text task over local running whisper instance", e);
    }
  }

  @Override
  public SpeechToTextEngineType getSpeechToTextEngineType() {
    return SpeechToTextEngineType.WHISPER_LOCAL;
  }

  @Override
  public Collection<SpeechToTextEngineOutputFormat> getAllowedOutputFormats() {
    return ALLOWED_OUTPUT_FORMATS;
  }

  private String[] createCommandLineArray(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration)
      throws SpeechToTextEngineModelNotFoundException {
    String deviceDescription = configuration.getDeviceConfiguration().createDeviceDescription();
    String modelIdentifier =
        getBestOptimizedModelIdentifierForDefinedLocale(speechToTextEngineExecutionConfiguration);
    String outputFormat = createOutputFormatValue(speechToTextEngineExecutionConfiguration);

    if (speechToTextEngineExecutionConfiguration.locale() != null) {
      String localeDescription = speechToTextEngineExecutionConfiguration.locale().getLanguage();
      return new String[] {
        configuration.getWhisperExecutable().toString(),
        "--verbose",
        "False",
        "--language",
        localeDescription,
        "--device",
        deviceDescription,
        "--model",
        modelIdentifier,
        "--output_format",
        outputFormat,
        "--output_dir",
        speechToTextEngineExecutionConfiguration.resultDirectoryPath().toString(),
        speechToTextEngineExecutionConfiguration.audioFilePath().toString()
      };
    }

    return new String[] {
      configuration.getWhisperExecutable().toString(),
      "--verbose",
      "False",
      "--device",
      deviceDescription,
      "--model",
      modelIdentifier,
      "--output_format",
      outputFormat,
      "--output_dir",
      speechToTextEngineExecutionConfiguration.resultDirectoryPath().toString(),
      speechToTextEngineExecutionConfiguration.audioFilePath().toString()
    };
  }

  private static String getBestOptimizedModelIdentifierForDefinedLocale(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration)
      throws SpeechToTextEngineModelNotFoundException {
    SpeechToTextEngineModel engineModel =
        WHISPER_MODELS_BY_IDENTIFIER.get(
            speechToTextEngineExecutionConfiguration.modelIdentifier());
    if (engineModel == null) {
      throw new SpeechToTextEngineModelNotFoundException(
          speechToTextEngineExecutionConfiguration.modelIdentifier());
    }

    if (speechToTextEngineExecutionConfiguration.locale() != null) {
      Optional<String> localeOptimizedModel =
          engineModel.getLocaleOptimizedModelVersionIdentifier(
              speechToTextEngineExecutionConfiguration.locale());
      if (localeOptimizedModel.isPresent()) {
        return localeOptimizedModel.get();
      }
    }

    return engineModel.getIdentifier();
  }

  private static String createOutputFormatValue(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration) {
    SpeechToTextEngineOutputFormat outputFormat =
        speechToTextEngineExecutionConfiguration.outputFormat();
    if (!ALLOWED_OUTPUT_FORMATS.contains(outputFormat)) {
      throw new IllegalArgumentException("Unsupported output format " + outputFormat);
    }

    return outputFormat.toString().toLowerCase();
  }

  private static Path createExpectedResultFilePath(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration) {
    String audioFileName =
        speechToTextEngineExecutionConfiguration.audioFilePath().getFileName().toString();
    int suffixStartIndex = audioFileName.lastIndexOf('.');
    String fileNameWithoutSuffix =
        suffixStartIndex > -1 ? audioFileName.substring(0, suffixStartIndex) : audioFileName;
    return speechToTextEngineExecutionConfiguration
        .resultDirectoryPath()
        .resolve(
            fileNameWithoutSuffix
                + "."
                + speechToTextEngineExecutionConfiguration.outputFormat().getFileSuffix());
  }
}
