package studio.nkodev.stt;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.adapter.grpc.SpeechToTextGrpcServer;
import studio.nkodev.stt.auth.CertificateAuthenticator;
import studio.nkodev.stt.auth.X509CertificateLoader;
import studio.nkodev.stt.config.SpeechToTextServiceConfiguration;
import studio.nkodev.stt.config.SpeechToTextServiceConfiguration.AuthenticationType;
import studio.nkodev.stt.config.SpeechToTextServiceConfigurationLoader;
import studio.nkodev.stt.db.DbAccess;
import studio.nkodev.stt.db.storage.SpeechToTextTaskDbStorage;
import studio.nkodev.stt.engine.SpeechToTextEngineRegistry;
import studio.nkodev.stt.engine.openai.OpenAIApiEngine;
import studio.nkodev.stt.engine.openai.client.OpenAIApiClient;
import studio.nkodev.stt.engine.openai.config.OpenAIApiSpeechToTextEngineConfiguration;
import studio.nkodev.stt.engine.whisper.LocalWhisperSpeechToTextEngine;
import studio.nkodev.stt.engine.whisper.config.LocalWhisperSpeechToTextEngineConfiguration;
import studio.nkodev.stt.service.SpeechToTextService;
import studio.nkodev.stt.service.StorageCleanupService;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.audio.AudioFileStorage;
import studio.nkodev.stt.storage.audio.SharedAudioFileStorage;
import studio.nkodev.stt.storage.audio.SharedStorageAudioFileProviderFactory;
import studio.nkodev.stt.storage.exception.StorageException;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultDirectoryManager;
import studio.nkodev.stt.storage.result.SpeechToTextTaskResultStorage;
import studio.nkodev.stt.storage.result.SpeechToTextTaskSharedResultStorage;
import studio.nkodev.stt.task.SpeechToTextTaskScheduler;

/**
 * Starter of the speech to text service
 *
 * @author Nico Kotlenga
 * @since 26.02.26
 */
public class SpeechToTextServiceStarter {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextServiceStarter.class);

  public static void main(String[] args)
      throws StorageException,
          IOException,
          InterruptedException,
          SQLException,
          CertificateException {
    logger.info("Starting speech-to-text-service");
    Path configurationFilePath = readConfigurationFilePath(args);
    SpeechToTextServiceConfiguration configuration =
        SpeechToTextServiceConfigurationLoader.load(configurationFilePath);

    logger.info("Initialize storage layer");
    AudioFileStorage audioFileStorage =
        new AudioFileStorage(
            configuration.audioFileStorageConfigurationSection().audioFileStorageLocation());

    SharedAudioFileStorage sharedAudioFileStorage = null;
    if (configuration.audioFileStorageConfigurationSection().sharedStorageLocation() != null) {
      logger.info("[Feature enabled]: Shared filesystem audio file storage");
      sharedAudioFileStorage =
          new SharedAudioFileStorage(
              configuration.audioFileStorageConfigurationSection().sharedStorageLocation());
    } else {
      logger.info("[Feature disabled]: Shared filesystem audio file storage");
    }

    SpeechToTextTaskSharedResultStorage speechToTextTaskSharedResultStorage = null;
    if (configuration.resultStorageConfigurationSection().sharedStorageLocation() != null) {
      logger.info("[Feature enabled]: Shared filesystem result storage");
      speechToTextTaskSharedResultStorage =
          new SpeechToTextTaskSharedResultStorage(
              configuration.resultStorageConfigurationSection().sharedStorageLocation());
    } else {
      logger.info("[Feature disabled]: Shared filesystem result storage");
    }

    SpeechToTextTaskResultDirectoryManager resultDirectoryManager =
        new SpeechToTextTaskResultDirectoryManager(
            configuration.resultStorageConfigurationSection().resultDirectoryLocation());
    SpeechToTextTaskResultStorage speechToTextTaskResultStorage =
        new SpeechToTextTaskResultStorage(resultDirectoryManager);

    DbAccess dbAccess = new DbAccess(configuration.databaseConfigurationSection());
    SpeechToTextTaskStorage speechToTextTaskStorage = new SpeechToTextTaskDbStorage(dbAccess);

    logger.info("Initialize speech to text engines");
    SpeechToTextEngineRegistry speechToTextEngineRegistry = new SpeechToTextEngineRegistry();

    Optional<LocalWhisperSpeechToTextEngineConfiguration>
        localWhisperSpeechToTextEngineConfiguration =
            configuration
                .engineConfigurationSection()
                .getLocalWhisperSpeechToTextEngineConfiguration();
    if (localWhisperSpeechToTextEngineConfiguration.isPresent()) {
      LocalWhisperSpeechToTextEngine localWhisperSpeechToTextEngine =
          new LocalWhisperSpeechToTextEngine(localWhisperSpeechToTextEngineConfiguration.get());
      speechToTextEngineRegistry.registerSpeechToTextEngine(localWhisperSpeechToTextEngine);
    }

    for (OpenAIApiSpeechToTextEngineConfiguration openAIApiEngineConfiguration :
        configuration.engineConfigurationSection().getOpenAIApiEngineConfigurations()) {
      OpenAIApiClient openAIApiClient = new OpenAIApiClient(openAIApiEngineConfiguration);
      OpenAIApiEngine openAIApiEngine =
          new OpenAIApiEngine(openAIApiEngineConfiguration, openAIApiClient);
      speechToTextEngineRegistry.registerSpeechToTextEngine(openAIApiEngine);
    }

    if (speechToTextEngineRegistry.getEngines().isEmpty()) {
      throw new IllegalArgumentException("No speech to text engine configuration found");
    }

    logger.info("Initialize task execution platform");
    SpeechToTextTaskScheduler speechToTextTaskScheduler =
        new SpeechToTextTaskScheduler(
            configuration.taskSchedulerConfigurationSection(),
            speechToTextEngineRegistry,
            speechToTextTaskStorage,
            speechToTextTaskResultStorage,
            audioFileStorage);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(speechToTextTaskScheduler::shutdown, "stt-task-scheduler-shutdown"));

    SpeechToTextService speechToTextService =
        new SpeechToTextService(
            speechToTextTaskStorage,
            speechToTextTaskResultStorage,
            audioFileStorage,
            speechToTextEngineRegistry);
    speechToTextService.setSharedResultStorage(speechToTextTaskSharedResultStorage);

    logger.info("Initialize adapter");
    SharedStorageAudioFileProviderFactory sharedStorageAudioFileProviderFactory =
        new SharedStorageAudioFileProviderFactory();
    sharedStorageAudioFileProviderFactory.setSharedAudioFileStorage(sharedAudioFileStorage);
    CertificateAuthenticator certificateAuthenticator = null;

    if (configuration
        .authenticationConfigurationSection()
        .type()
        .equals(AuthenticationType.CERTIFICATE)) {
      certificateAuthenticator =
          new CertificateAuthenticator(
              X509CertificateLoader.load(
                  configuration
                      .authenticationConfigurationSection()
                      .getRootCertificatePath()
                      .orElseThrow()));
    }

    SpeechToTextGrpcServer grpcServer =
        new SpeechToTextGrpcServer(
            configuration.grpcServerConfigurationSection(),
            certificateAuthenticator,
            speechToTextService,
            sharedStorageAudioFileProviderFactory);
    grpcServer.start();

    logger.info("Initialize task cleanup process");
    StorageCleanupService storageCleanupService =
        new StorageCleanupService(
            speechToTextTaskStorage, audioFileStorage, speechToTextTaskResultStorage);

    logger.info("Starting speech-to-text-service completed");
    grpcServer.blockUntilShutdown();
  }

  private static Path readConfigurationFilePath(String[] args) {
    if (args.length != 1 || args[0].isBlank()) {
      throw new IllegalArgumentException(
          "Expected exactly one program argument containing the path to the YAML configuration file");
    }
    return Path.of(args[0]);
  }
}
