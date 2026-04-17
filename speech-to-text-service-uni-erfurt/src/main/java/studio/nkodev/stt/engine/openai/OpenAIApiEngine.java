package studio.nkodev.stt.engine.openai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
import studio.nkodev.stt.engine.openai.client.OpenAIApiClient;
import studio.nkodev.stt.engine.openai.client.OpenAIApiClientException;
import studio.nkodev.stt.engine.openai.config.OpenAIApiSpeechToTextEngineConfiguration;

/**
 * Speech to text engine using an external OpenAI compatible transcription API.
 *
 * @author Nico Kotlenga
 * @since 17.04.26
 */
public class OpenAIApiEngine implements SpeechToTextEngine {

  private static final Logger logger = LoggerFactory.getLogger(OpenAIApiEngine.class);
  private static final String UPLOADED_FILE_NAME_TEMPLATE =
      "speech-to-text-service-uni-erfurt-task-%d.audio";
  private static final Set<SpeechToTextEngineOutputFormat> ALLOWED_OUTPUT_FORMATS =
      Set.of(
          SpeechToTextEngineOutputFormat.TXT,
          SpeechToTextEngineOutputFormat.JSON,
          SpeechToTextEngineOutputFormat.SRT,
          SpeechToTextEngineOutputFormat.VTT);

  private final OpenAIApiSpeechToTextEngineConfiguration configuration;
  private final OpenAIApiClient openAIApiClient;

  public OpenAIApiEngine(
      OpenAIApiSpeechToTextEngineConfiguration configuration, OpenAIApiClient openAIApiClient) {
    this.configuration = configuration;
    this.openAIApiClient = openAIApiClient;
  }

  @Override
  public Collection<SpeechToTextEngineModel> getModels() {
    try {
      return openAIApiClient.getAvailableTranscriptionModelIdentifiers().stream()
          .map(SpeechToTextEngineModel::new)
          .collect(Collectors.toUnmodifiableSet());
    } catch (OpenAIApiClientException exception) {
      throw new IllegalStateException("Failed to load models from OpenAI API", exception);
    }
  }

  @Override
  public Optional<SpeechToTextEngineModel> getModelByIdentifier(String identifier) {
    return getModels().stream().filter(model -> model.getIdentifier().equals(identifier)).findFirst();
  }

  @Override
  public void executeSpeechToTextTask(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration)
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException {
    verifyOutputFormat(speechToTextEngineExecutionConfiguration.outputFormat());

    String modelIdentifier = speechToTextEngineExecutionConfiguration.modelIdentifier();
    if (getModelByIdentifier(modelIdentifier).isEmpty()) {
      throw new SpeechToTextEngineModelNotFoundException(modelIdentifier);
    }

    String uploadedFileId = null;
    try {
      uploadedFileId =
          openAIApiClient.uploadFile(
              speechToTextEngineExecutionConfiguration.audioFilePath(),
              createUploadFileName(speechToTextEngineExecutionConfiguration.taskId()));
      String transcriptionResult =
          openAIApiClient.startTranscription(
              uploadedFileId,
              modelIdentifier,
              speechToTextEngineExecutionConfiguration.outputFormat(),
              speechToTextEngineExecutionConfiguration.locale());
      Path resultFilePath = createResultFilePath(speechToTextEngineExecutionConfiguration);
      Files.writeString(resultFilePath, transcriptionResult, StandardCharsets.UTF_8);
      if (!Files.exists(resultFilePath)) {
        throw new SpeechToTextEngineMissingResultException(
            speechToTextEngineExecutionConfiguration.outputFormat(),
            speechToTextEngineExecutionConfiguration.audioFilePath());
      }
    } catch (IOException | OpenAIApiClientException exception) {
      throw new SpeechToTextEngineExecutionException(
          "Error while executing speech to text task using OpenAI API engine", exception);
    } finally {
      deleteUploadedFileIfRequired(uploadedFileId);
    }
  }

  @Override
  public String getIdentifier() {
    return configuration.getIdentifier();
  }

  @Override
  public String getEngineName() {
    return getSpeechToTextEngineType().getEngineTypeName();
  }

  @Override
  public SpeechToTextEngineType getSpeechToTextEngineType() {
    return SpeechToTextEngineType.OPENAI_API;
  }

  @Override
  public Collection<SpeechToTextEngineOutputFormat> getAllowedOutputFormats() {
    return ALLOWED_OUTPUT_FORMATS;
  }

  private static void verifyOutputFormat(SpeechToTextEngineOutputFormat outputFormat) {
    if (!ALLOWED_OUTPUT_FORMATS.contains(outputFormat)) {
      throw new IllegalArgumentException("Unsupported output format " + outputFormat);
    }
  }

  private static String createUploadFileName(long taskId) {
    return String.format(UPLOADED_FILE_NAME_TEMPLATE, taskId);
  }

  private static Path createResultFilePath(
      SpeechToTextEngineExecutionConfiguration speechToTextEngineExecutionConfiguration) {
    return speechToTextEngineExecutionConfiguration
        .resultDirectoryPath()
        .resolve(
            "speech-to-text-service-uni-erfurt-task-"
                + speechToTextEngineExecutionConfiguration.taskId()
                + "."
                + speechToTextEngineExecutionConfiguration.outputFormat().getFileSuffix());
  }

  private void deleteUploadedFileIfRequired(String uploadedFileId) {
    if (uploadedFileId == null || uploadedFileId.isBlank()) {
      return;
    }

    try {
      openAIApiClient.deleteFile(uploadedFileId);
    } catch (Exception exception) {
      logger.warn("Failed to delete uploaded file {} for engine {}", uploadedFileId, getIdentifier(), exception);
    }
  }
}
