package studio.nkodev.stt.engine.openai.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.openai.config.OpenAIApiSpeechToTextEngineConfiguration;

/**
 * Minimal HTTP client for an OpenAI compatible transcription API.
 *
 * @author Nico Kotlenga
 * @since 17.04.26
 */
public class OpenAIApiClient {

  private static final String FILES_ENDPOINT = "/v1/files";
  private static final String MODELS_ENDPOINT = "/v1/models";
  private static final String TRANSCRIPTIONS_ENDPOINT = "/v1/audio/transcriptions";
  private static final Gson GSON = new Gson();
  private static final Pattern JSON_STRING_FIELD_PATTERN_TEMPLATE =
      Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

  private final HttpClient httpClient;
  private final URI apiBaseUrl;
  private final String apiToken;

  public OpenAIApiClient(OpenAIApiSpeechToTextEngineConfiguration configuration) {
    this(HttpClient.newHttpClient(), configuration);
  }

  OpenAIApiClient(
      HttpClient httpClient, OpenAIApiSpeechToTextEngineConfiguration configuration) {
    this.httpClient = Objects.requireNonNull(httpClient, "No http client provided");
    Objects.requireNonNull(configuration, "No OpenAI API configuration provided");
    this.apiBaseUrl = sanitizeBaseUrl(configuration.getApiBaseUrl());
    this.apiToken = requireToken(configuration.getApiToken());
  }

  public String uploadFile(Path filePath, String fileName) throws OpenAIApiClientException {
    Objects.requireNonNull(filePath, "No file path provided");
    requireString(fileName, "No file name provided");

    MultipartFormDataBodyPublisher multipartBodyPublisher = new MultipartFormDataBodyPublisher();
    try {
      multipartBodyPublisher.addTextPart("purpose", "user_data");
      multipartBodyPublisher.addFilePart(
          "file", fileName, "application/octet-stream", filePath);
    } catch (IOException exception) {
      throw new OpenAIApiClientException("Failed to read audio file for upload", exception);
    }

    HttpRequest request =
        baseRequestBuilder(resolve(FILES_ENDPOINT))
            .header("Content-Type", multipartBodyPublisher.getContentType())
            .POST(multipartBodyPublisher.build())
            .build();
    String responseBody = sendRequest(request);

    String fileId = extractOptionalStringField(responseBody, "id");
    if (fileId == null || fileId.isBlank()) {
      throw new OpenAIApiClientException("OpenAI API upload response did not contain a file id");
    }
    return fileId;
  }

  public void deleteFile(String fileId) throws OpenAIApiClientException {
    requireString(fileId, "No file id provided");

    HttpRequest request =
        baseRequestBuilder(resolve(FILES_ENDPOINT + "/" + encode(fileId))).DELETE().build();
    sendRequest(request);
  }

  public String startTranscription(
      String fileId,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale)
      throws OpenAIApiClientException {
    requireString(fileId, "No file id provided");
    requireString(modelIdentifier, "No model identifier provided");
    Objects.requireNonNull(outputFormat, "No output format provided");

    String requestBody = createTranscriptionRequestBody(fileId, modelIdentifier, outputFormat, locale);

    HttpRequest request =
        baseRequestBuilder(resolve(TRANSCRIPTIONS_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    String responseBody = sendRequest(request);

    return normalizeTranscriptionResponse(responseBody, outputFormat);
  }

  public Collection<String> getAvailableTranscriptionModelIdentifiers()
      throws OpenAIApiClientException {
    HttpRequest request = baseRequestBuilder(resolve(MODELS_ENDPOINT)).GET().build();
    String responseBody = sendRequest(request);

    String dataArray = extractJsonArrayByFieldName(responseBody, "data");
    if (dataArray == null) {
      throw new OpenAIApiClientException("OpenAI API model response did not contain data");
    }

    List<String> typedTranscriptionModels = new ArrayList<>();
    List<String> inferredTranscriptionModels = new ArrayList<>();
    List<String> allModels = new ArrayList<>();

    for (String currentModel : extractTopLevelObjects(dataArray)) {
      String modelIdentifier = extractOptionalStringField(currentModel, "id");
      if (modelIdentifier == null || modelIdentifier.isBlank()) {
        continue;
      }
      allModels.add(modelIdentifier);

      String modelType = extractOptionalStringField(currentModel, "type");
      if (modelType != null && "transcription".equalsIgnoreCase(modelType)) {
        typedTranscriptionModels.add(modelIdentifier);
        continue;
      }

      // Currently we can't filter by usage type. So this is the only possibility to get available models
      String normalizedIdentifier = modelIdentifier.toLowerCase(Locale.ROOT);
      if (normalizedIdentifier.contains("transcribe")
          || normalizedIdentifier.contains("whisper")) {
        inferredTranscriptionModels.add(modelIdentifier);
      }
    }

    if (!typedTranscriptionModels.isEmpty()) {
      return typedTranscriptionModels;
    }
    if (!inferredTranscriptionModels.isEmpty()) {
      return inferredTranscriptionModels;
    }
    return allModels;
  }

  private HttpRequest.Builder baseRequestBuilder(URI requestUri) {
    return HttpRequest.newBuilder(requestUri)
        .header("Authorization", "Bearer " + apiToken)
        .header("Accept", "application/json, text/plain, */*");
  }

  private String sendRequest(HttpRequest request) throws OpenAIApiClientException {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new OpenAIApiClientException("Failed to communicate with OpenAI API", exception);
    }

    int statusCode = response.statusCode();
    if (statusCode < 200 || statusCode > 299) {
      throw new OpenAIApiClientException(
          "OpenAI API request failed with status " + statusCode + ": " + response.body());
    }
    return response.body();
  }

  private static URI sanitizeBaseUrl(URI apiBaseUrl) {
    Objects.requireNonNull(apiBaseUrl, "No API base URL provided");
    String uriString = apiBaseUrl.toString();
    if (uriString.endsWith("/")) {
      return URI.create(uriString.substring(0, uriString.length() - 1));
    }
    return apiBaseUrl;
  }

  private URI resolve(String path) {
    return URI.create(apiBaseUrl + path);
  }

  private static String mapOutputFormat(SpeechToTextEngineOutputFormat outputFormat) {
    return switch (outputFormat) {
      case TXT -> "text";
      case JSON -> "json";
      case SRT -> "srt";
      case VTT -> "vtt";
      case TSV ->
          throw new IllegalArgumentException("TSV output format is not supported by OpenAI API");
    };
  }

  private static String normalizeTranscriptionResponse(
      String responseBody, SpeechToTextEngineOutputFormat outputFormat)
      throws OpenAIApiClientException {
    if (outputFormat == SpeechToTextEngineOutputFormat.JSON) {
      return responseBody;
    }

    String trimmedResponseBody = responseBody.trim();
    if (trimmedResponseBody.startsWith("{")) {
      try {
        JsonObject responseJson = GSON.fromJson(responseBody, JsonObject.class);
        if (responseJson != null) {
          JsonElement textElement = responseJson.get("text");
          if (textElement != null && !textElement.isJsonNull()) {
            return textElement.getAsString();
          }
        }
      } catch (Exception exception) {
        throw new OpenAIApiClientException(
            "Failed to parse transcription response body as JSON", exception);
      }
    }

    return responseBody;
  }

  private static String createTranscriptionRequestBody(
      String fileId,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale) {
    JsonObject requestBody = new JsonObject();
    requestBody.addProperty("file", fileId);
    requestBody.addProperty("model", modelIdentifier);
    requestBody.addProperty("response_format", mapOutputFormat(outputFormat));
    if (locale != null) {
      requestBody.addProperty("language", locale.getLanguage());
    }
    return GSON.toJson(requestBody);
  }

  private static String extractJsonArrayByFieldName(String json, String fieldName) {
    int fieldIndex = json.indexOf("\"" + fieldName + "\"");
    if (fieldIndex < 0) {
      return null;
    }

    int arrayStartIndex = json.indexOf('[', fieldIndex);
    if (arrayStartIndex < 0) {
      return null;
    }

    int depth = 0;
    for (int index = arrayStartIndex; index < json.length(); index++) {
      char currentCharacter = json.charAt(index);
      if (currentCharacter == '[') {
        depth++;
      } else if (currentCharacter == ']') {
        depth--;
        if (depth == 0) {
          return json.substring(arrayStartIndex, index + 1);
        }
      }
    }

    return null;
  }

  private static List<String> extractTopLevelObjects(String jsonArray) {
    List<String> objects = new ArrayList<>();
    int depth = 0;
    int objectStartIndex = -1;

    for (int index = 0; index < jsonArray.length(); index++) {
      char currentCharacter = jsonArray.charAt(index);
      if (currentCharacter == '{') {
        if (depth == 0) {
          objectStartIndex = index;
        }
        depth++;
      } else if (currentCharacter == '}') {
        depth--;
        if (depth == 0 && objectStartIndex >= 0) {
          objects.add(jsonArray.substring(objectStartIndex, index + 1));
          objectStartIndex = -1;
        }
      }
    }

    return objects;
  }

  private static String extractOptionalStringField(String json, String fieldName) {
    Pattern fieldPattern =
        Pattern.compile(String.format(JSON_STRING_FIELD_PATTERN_TEMPLATE.pattern(), fieldName));
    Matcher matcher = fieldPattern.matcher(json);
    if (!matcher.find()) {
      return null;
    }
    return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static String requireToken(String apiToken) {
    return requireString(apiToken, "No API token provided");
  }

  private static String requireString(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static final class MultipartFormDataBodyPublisher {

    private static final String CRLF = "\r\n";

    private final String boundary = "----stt-openai-" + System.nanoTime();
    private final List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
    private boolean built;

    public void addTextPart(String name, String value) {
      verifyNotBuilt();
      parts.add(
          HttpRequest.BodyPublishers.ofString(
              "--"
                  + boundary
                  + CRLF
                  + "Content-Disposition: form-data; name=\""
                  + name
                  + "\""
                  + CRLF
                  + CRLF
                  + value
                  + CRLF,
              StandardCharsets.UTF_8));
    }

    public void addFilePart(String name, String fileName, String contentType, Path filePath)
        throws IOException {
      verifyNotBuilt();
      parts.add(
          HttpRequest.BodyPublishers.ofString(
              "--"
                  + boundary
                  + CRLF
                  + "Content-Disposition: form-data; name=\""
                  + name
                  + "\"; filename=\""
                  + fileName
                  + "\""
                  + CRLF
                  + "Content-Type: "
                  + contentType
                  + CRLF
                  + CRLF,
              StandardCharsets.UTF_8));
      parts.add(HttpRequest.BodyPublishers.ofFile(filePath));
      parts.add(HttpRequest.BodyPublishers.ofString(CRLF, StandardCharsets.UTF_8));
    }

    public HttpRequest.BodyPublisher build() {
      verifyNotBuilt();
      built = true;
      parts.add(
          HttpRequest.BodyPublishers.ofString(
              "--" + boundary + "--" + CRLF, StandardCharsets.UTF_8));
      return HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
    }

    public String getContentType() {
      return "multipart/form-data; boundary=" + boundary;
    }

    private void verifyNotBuilt() {
      if (built) {
        throw new IllegalStateException("Multipart request already built");
      }
    }
  }
}
