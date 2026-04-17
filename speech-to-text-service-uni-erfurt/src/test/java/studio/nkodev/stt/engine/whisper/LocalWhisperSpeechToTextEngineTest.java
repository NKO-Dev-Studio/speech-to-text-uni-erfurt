package studio.nkodev.stt.engine.whisper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.engine.api.SpeechToTextEngineExecutionConfiguration;
import studio.nkodev.stt.engine.api.SpeechToTextEngineModel;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineExecutionException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineMissingResultException;
import studio.nkodev.stt.engine.exception.SpeechToTextEngineModelNotFoundException;
import studio.nkodev.stt.engine.whisper.config.LocalWhisperSpeechToTextEngineConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineCpuDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineGpuDeviceConfiguration;
import studio.nkodev.stt.engine.whisper.config.device.LocalWhisperSpeechToTextEngineMpsDevice;

/**
 * Testsuite of {@link LocalWhisperSpeechToTextEngine}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public class LocalWhisperSpeechToTextEngineTest {

  private static final Path WHISPER_CLI_MOCK_PATH =
      Path.of(
          Objects.requireNonNull(
                  LocalWhisperSpeechToTextEngine.class.getResource("whisper-cli-mock.sh"))
              .getPath());
  private static final Path WHISPER_CLI_NOISY_MOCK_PATH =
      Path.of(
          Objects.requireNonNull(
                  LocalWhisperSpeechToTextEngine.class.getResource("whisper-cli-noisy-mock.sh"))
              .getPath());
  private static final Path WHISPER_CLI_RESULT_JSON_OUTPUT_PATH =
      Path.of("/tmp/whisper-mock-arguments.json");
  private static final Path TRUE_EXECUTABLE_PATH = Path.of("/usr/bin/true");

  private static Gson gson;
  private static LocalWhisperSpeechToTextEngine whisperSpeechToTextEngine;
  private static Path testResultDirectory;
  private static Path audioFilePath;

  @BeforeAll
  public static void setUp() throws IOException {
    gson = new Gson();
    testResultDirectory = Files.createTempDirectory("stt-local-whisper-engine-test");
    audioFilePath = testResultDirectory.resolve("audio.wav");
    Files.createFile(testResultDirectory.resolve("audio.txt"));
  }

  @BeforeAll
  public static void setup() {
    gson = new Gson();

    LocalWhisperSpeechToTextEngineConfiguration configuration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return WHISPER_CLI_MOCK_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(0);
          }
        };
    whisperSpeechToTextEngine = new LocalWhisperSpeechToTextEngine(configuration);
  }

  @Test
  public void shouldReturnCorrectModelsByIdentifier() {
    Set<String> existingModelIdentifier =
        Set.of("tiny", "base", "small", "medium", "large", "turbo");
    for (String currentExistingModelIdentifier : existingModelIdentifier) {
      Optional<SpeechToTextEngineModel> currentModel =
          whisperSpeechToTextEngine.getModelByIdentifier(currentExistingModelIdentifier);
      assertTrue(currentModel.isPresent());
      assertEquals(currentExistingModelIdentifier, currentModel.get().getIdentifier());
    }

    Optional<SpeechToTextEngineModel> notExistingModel =
        whisperSpeechToTextEngine.getModelByIdentifier("not-existing");
    assertFalse(notExistingModel.isPresent());
  }

  @Test
  public void shouldSelectCorrectModelForSpecifiedLocale()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    Set<String> modelIdentifiersWithEnglishOptimizedModels =
        Set.of("tiny", "base", "small", "medium");
    for (String currentModelIdentifier : modelIdentifiersWithEnglishOptimizedModels) {
      SpeechToTextEngineExecutionConfiguration taskConfiguration =
          new SpeechToTextEngineExecutionConfiguration(
              currentModelIdentifier,
              audioFilePath,
              testResultDirectory,
              SpeechToTextEngineOutputFormat.TXT,
              Locale.ENGLISH);
      whisperSpeechToTextEngine.executeSpeechToTextTask(taskConfiguration);
      WhisperProgramArgument programArgument = readWhisperMockJsonResult();
      assertEquals(currentModelIdentifier + ".en", programArgument.model());
    }
  }

  @Test
  public void shouldUseCorrectDeviceConfiguration()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    LocalWhisperSpeechToTextEngineConfiguration cpuConfiguration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return WHISPER_CLI_MOCK_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(0);
          }
        };
    LocalWhisperSpeechToTextEngineConfiguration mpsConfiguration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return WHISPER_CLI_MOCK_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineMpsDevice();
          }
        };
    LocalWhisperSpeechToTextEngineConfiguration gpuConfiguration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return WHISPER_CLI_MOCK_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineGpuDeviceConfiguration();
          }
        };

    LocalWhisperSpeechToTextEngine cpuEngine = new LocalWhisperSpeechToTextEngine(cpuConfiguration);
    LocalWhisperSpeechToTextEngine mpsEngine = new LocalWhisperSpeechToTextEngine(mpsConfiguration);
    LocalWhisperSpeechToTextEngine gpuEngine = new LocalWhisperSpeechToTextEngine(gpuConfiguration);

    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);

    cpuEngine.executeSpeechToTextTask(taskConfiguration);
    WhisperProgramArgument cpuEngineResult = readWhisperMockJsonResult();

    mpsEngine.executeSpeechToTextTask(taskConfiguration);
    WhisperProgramArgument mpsEngineResult = readWhisperMockJsonResult();

    gpuEngine.executeSpeechToTextTask(taskConfiguration);
    WhisperProgramArgument gpuEngineResult = readWhisperMockJsonResult();

    assertEquals("cpu", cpuEngineResult.device());
    assertEquals("gpu", gpuEngineResult.device());
    assertEquals("mps", mpsEngineResult.device());
  }

  @Test
  public void shouldWriteCorrectOutputDirectoryPath()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);
    whisperSpeechToTextEngine.executeSpeechToTextTask(taskConfiguration);

    WhisperProgramArgument programArgument = readWhisperMockJsonResult();
    assertEquals(testResultDirectory.toString(), programArgument.outputDir());
  }

  @Test
  public void shouldWriteCorrectAudioFilePath()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);
    whisperSpeechToTextEngine.executeSpeechToTextTask(taskConfiguration);

    WhisperProgramArgument programArgument = readWhisperMockJsonResult();
    assertEquals(audioFilePath.toString(), programArgument.audioFilePath());
  }

  @Test
  public void shouldWriteCorrectLanguage()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    SpeechToTextEngineExecutionConfiguration deTaskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base",
            audioFilePath,
            testResultDirectory,
            SpeechToTextEngineOutputFormat.TXT,
            Locale.GERMANY);
    SpeechToTextEngineExecutionConfiguration enTaskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base",
            audioFilePath,
            testResultDirectory,
            SpeechToTextEngineOutputFormat.TXT,
            Locale.ENGLISH);

    whisperSpeechToTextEngine.executeSpeechToTextTask(deTaskConfiguration);
    WhisperProgramArgument deProgramArgument = readWhisperMockJsonResult();

    whisperSpeechToTextEngine.executeSpeechToTextTask(enTaskConfiguration);
    WhisperProgramArgument enProgramArgument = readWhisperMockJsonResult();

    assertEquals("de", deProgramArgument.language());
    assertEquals("en", enProgramArgument.language());
  }

  @Test
  public void shouldWriteCorrectOutputFormat()
      throws SpeechToTextEngineModelNotFoundException,
          SpeechToTextEngineExecutionException,
          SpeechToTextEngineMissingResultException,
          FileNotFoundException {
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);
    whisperSpeechToTextEngine.executeSpeechToTextTask(taskConfiguration);

    WhisperProgramArgument programArgument = readWhisperMockJsonResult();
    assertEquals("txt", programArgument.output_format());
  }

  @Test
  public void shouldThrowExceptionOnIllegalModelSpecification() {
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "not-existing",
            audioFilePath,
            testResultDirectory,
            SpeechToTextEngineOutputFormat.TXT);

    assertThrows(
        SpeechToTextEngineModelNotFoundException.class,
        () -> whisperSpeechToTextEngine.executeSpeechToTextTask(taskConfiguration));
  }

  @Test
  public void shouldThrowExceptionOnEmptyOutputFormatDefinition() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SpeechToTextEngineExecutionConfiguration(
                "base", audioFilePath, testResultDirectory, null));
  }

  @Test
  public void shouldThrowExceptionWhenExpectedResultFileIsMissing() {
    LocalWhisperSpeechToTextEngineConfiguration configuration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return TRUE_EXECUTABLE_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(0);
          }
        };
    LocalWhisperSpeechToTextEngine engine = new LocalWhisperSpeechToTextEngine(configuration);
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);
    Path expectedResultFile = testResultDirectory.resolve("audio.txt");
    try {
      Files.deleteIfExists(expectedResultFile);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }

    assertThrows(
        SpeechToTextEngineMissingResultException.class,
        () -> engine.executeSpeechToTextTask(taskConfiguration));
  }

  @Test
  public void shouldHandleLargeProcessOutputWithoutBlocking() {
    LocalWhisperSpeechToTextEngineConfiguration configuration =
        new LocalWhisperSpeechToTextEngineConfiguration() {
          @Override
          public Path getWhisperExecutable() {
            return WHISPER_CLI_NOISY_MOCK_PATH;
          }

          @Override
          public LocalWhisperSpeechToTextEngineDeviceConfiguration getDeviceConfiguration() {
            return new LocalWhisperSpeechToTextEngineCpuDeviceConfiguration(0);
          }
        };
    LocalWhisperSpeechToTextEngine engine = new LocalWhisperSpeechToTextEngine(configuration);
    SpeechToTextEngineExecutionConfiguration taskConfiguration =
        new SpeechToTextEngineExecutionConfiguration(
            "base", audioFilePath, testResultDirectory, SpeechToTextEngineOutputFormat.TXT);

    assertTimeoutPreemptively(
        Duration.ofSeconds(3), () -> engine.executeSpeechToTextTask(taskConfiguration));
  }

  private static WhisperProgramArgument readWhisperMockJsonResult() throws FileNotFoundException {
    JsonReader jsonReader =
        new JsonReader(new FileReader(WHISPER_CLI_RESULT_JSON_OUTPUT_PATH.toFile()));
    return gson.fromJson(jsonReader, WhisperProgramArgument.class);
  }
}
