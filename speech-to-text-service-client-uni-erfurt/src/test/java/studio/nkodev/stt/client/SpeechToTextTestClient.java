package studio.nkodev.stt.client;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientAuthenticationConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfigurationBuilder;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;

/**
 * Example client usage
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 04.04.26
 */
public class SpeechToTextTestClient {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTestClient.class);

  public static void main(String[] args) {
    CountDownLatch countDownLatch = new CountDownLatch(1);

    SpeechToTextServiceClientConfiguration clientConfiguration =
        new SpeechToTextServiceClientConfigurationBuilder(
                "localhost",
                8080,
                Path.of(
                    "/Users/nicokotlenga/Projects/nko-dev-studio/speech-to-text/speech-to-text-service/certificates/dev/root-ca.cert.pem"),
                SpeechToTextTransferConfigurationFactory.streaming(),
                SpeechToTextTransferConfigurationFactory.streaming())
            .authenticationConfiguration(
                SpeechToTextServiceClientAuthenticationConfiguration.builder(
                        Path.of(
                            "/Users/nicokotlenga/Projects/nko-dev-studio/speech-to-text/speech-to-text-service/certificates/dev/speech-to-text-client.cert.pem"),
                        Path.of(
                            "/Users/nicokotlenga/Projects/nko-dev-studio/speech-to-text/speech-to-text-service/certificates/dev/speech-to-text-client.key.pem"))
                    .build())
            .build();

    try (SpeechToTextServiceClient client = new SpeechToTextServiceClient(clientConfiguration); ) {
      long taskId =
          client.startSpeechToTextTask(
              Path.of("/Users/nicokotlenga/Music/AU-20260406-0935-4100.mp3"),
              "WHISPER_LOCAL",
              "turbo",
              SpeechToTextEngineOutputFormat.SRT);
      client.observeTask(
          taskId,
          (taskId1, speechToTextTaskState) -> {
            logger.info("Task with id {} switched to state {}", taskId1, speechToTextTaskState);
            if (SpeechToTextTaskState.DONE.equals(speechToTextTaskState)) {
              client.saveResultsOfTask(
                  taskId1,
                  Path.of(
                      "/Users/nicokotlenga/Documents/Projects/TIMAAT/speech-to-text-service/working-directory/client-results/result.json"));
              countDownLatch.countDown();
            }
            if (SpeechToTextTaskState.FAILED.equals(speechToTextTaskState)) {
              countDownLatch.countDown();
            }
          });
      countDownLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
