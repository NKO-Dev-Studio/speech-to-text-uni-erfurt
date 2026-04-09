package studio.nkodev.stt.client.adapter.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import studio.nkodev.stt.client.api.SpeechToTextEngine;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;
import studio.nkodev.stt.proto.SpeechToTextService;

class GrpcSpeechToTextMapperTest {

  @Test
  void shouldMapGrpcTaskStateDoneToClientDone() {
    assertEquals(
        SpeechToTextTaskState.DONE,
        GrpcSpeechToTextMapper.mapTaskState(SpeechToTextService.SpeechToTextTaskState.DONE));
  }

  @Test
  void shouldMapClientOutputFormatToGrpcOutputFormat() {
    assertEquals(
        SpeechToTextService.SpeechToTextEngineOutputFormat.JSON,
        GrpcSpeechToTextMapper.mapOutputFormat(SpeechToTextEngineOutputFormat.JSON));
  }
}
