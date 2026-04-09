package studio.nkodev.stt.storage.audio;


/**
 * Generic chunk of an audio file transferred to the service.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public record AudioFileChunk(byte[] content, boolean lastChunk) {

  public AudioFileChunk {
    if (content == null) {
      throw new IllegalArgumentException("No chunk content provided");
    }
  }
}
