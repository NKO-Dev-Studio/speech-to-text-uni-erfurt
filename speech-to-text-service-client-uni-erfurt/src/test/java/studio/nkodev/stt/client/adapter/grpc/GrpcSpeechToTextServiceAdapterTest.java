package studio.nkodev.stt.client.adapter.grpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import studio.nkodev.stt.proto.SpeechToTextService;

class GrpcSpeechToTextServiceAdapterTest {

  @Test
  void shouldStreamAllAudioBytesWithoutTruncationOrReordering() throws Exception {
    byte[] audioContent = new byte[200_000];
    for (int index = 0; index < audioContent.length; index++) {
      audioContent[index] = (byte) (index % 251);
    }

    List<SpeechToTextService.AudioFileChunk> sentChunks = new ArrayList<>();

    GrpcSpeechToTextServiceAdapter.streamAudioFileChunks(
        42L,
        new ByteArrayInputStream(audioContent),
        new CollectingStreamObserver(sentChunks),
        64 * 1024);

    assertEquals(4, sentChunks.size());
    assertEquals(0, sentChunks.getFirst().getChunkIndex());
    assertEquals(3, sentChunks.getLast().getChunkIndex());
    assertEquals(false, sentChunks.getFirst().getLastChunk());
    assertEquals(true, sentChunks.getLast().getLastChunk());

    ByteArrayOutputStream reconstructedContent = new ByteArrayOutputStream();
    for (SpeechToTextService.AudioFileChunk sentChunk : sentChunks) {
      reconstructedContent.write(sentChunk.getContent().toByteArray());
    }

    assertArrayEquals(audioContent, reconstructedContent.toByteArray());
  }

  @Test
  void shouldSendSingleEmptyLastChunkForEmptyAudioFile() throws Exception {
    List<SpeechToTextService.AudioFileChunk> sentChunks = new ArrayList<>();

    GrpcSpeechToTextServiceAdapter.streamAudioFileChunks(
        42L,
        new ByteArrayInputStream(new byte[0]),
        new CollectingStreamObserver(sentChunks),
        64 * 1024);

    assertEquals(1, sentChunks.size());
    assertEquals(0, sentChunks.getFirst().getChunkIndex());
    assertEquals(true, sentChunks.getFirst().getLastChunk());
    assertEquals(0, sentChunks.getFirst().getContent().size());
  }

  private record CollectingStreamObserver(List<SpeechToTextService.AudioFileChunk> sentChunks)
      implements StreamObserver<SpeechToTextService.AudioFileChunk> {

    @Override
    public void onNext(SpeechToTextService.AudioFileChunk value) {
      sentChunks.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      throw new AssertionError("Unexpected stream failure", throwable);
    }

    @Override
    public void onCompleted() {}
  }
}
