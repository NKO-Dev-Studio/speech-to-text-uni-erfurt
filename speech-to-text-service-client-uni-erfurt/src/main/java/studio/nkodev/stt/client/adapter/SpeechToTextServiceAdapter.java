package studio.nkodev.stt.client.adapter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;

/**
 * Adapter connecting the client library to a speech-to-text-service transport.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public interface SpeechToTextServiceAdapter extends AutoCloseable {

  long startSpeechToTextTask(
      Path audioFile,
      String engineIdentifier,
      String modelIdentifier,
      SpeechToTextEngineOutputFormat outputFormat,
      Locale locale);

  SpeechToTextTaskState getStateOfTask(long taskId);

  void saveResultsOfTask(long taskId, Path resultPath);

  Collection<SpeechToTextEngine> getAvailableEngines();

  @Override
  void close();
}
