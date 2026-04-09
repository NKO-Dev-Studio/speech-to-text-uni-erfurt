package studio.nkodev.stt.client.adapter.grpc;

import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.proto.SpeechToTextService;

/**
 * Maps the public client API to the protobuf transport types.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 03.04.26
 */
public final class GrpcSpeechToTextMapper {

  private GrpcSpeechToTextMapper() {}

  public static SpeechToTextService.SpeechToTextEngineOutputFormat mapOutputFormat(
      SpeechToTextEngineOutputFormat speechToTextEngineOutputFormat) {
    if (speechToTextEngineOutputFormat == null) {
      throw new IllegalArgumentException("No speech-to-text engine output format provided");
    }

    return SpeechToTextService.SpeechToTextEngineOutputFormat.valueOf(
        speechToTextEngineOutputFormat.name());
  }

  public static SpeechToTextEngineOutputFormat mapOutputFormat(
      SpeechToTextService.SpeechToTextEngineOutputFormat speechToTextEngineOutputFormat) {
    if (speechToTextEngineOutputFormat == null) {
      throw new IllegalArgumentException("No speech-to-text engine output format provided");
    }

    return SpeechToTextEngineOutputFormat.valueOf(speechToTextEngineOutputFormat.name());
  }

  public static SpeechToTextTaskState mapTaskState(
      SpeechToTextService.SpeechToTextTaskState speechToTextTaskState) {
    if (speechToTextTaskState == null) {
      throw new IllegalArgumentException("No speech-to-text task state provided");
    }

    return switch (speechToTextTaskState) {
      case WAITING_FOR_AUDIO -> SpeechToTextTaskState.WAITING_FOR_AUDIO_FILE;
      case PENDING -> SpeechToTextTaskState.PENDING;
      case RUNNING -> SpeechToTextTaskState.RUNNING;
      case DONE -> SpeechToTextTaskState.DONE;
      case FAILED -> SpeechToTextTaskState.FAILED;
    };
  }
}
