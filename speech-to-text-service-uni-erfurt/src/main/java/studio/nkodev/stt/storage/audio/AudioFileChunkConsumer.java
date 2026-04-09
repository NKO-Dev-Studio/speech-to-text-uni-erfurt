package studio.nkodev.stt.storage.audio;

import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Generic consumer allowing adapters to provide an audio file chunk by chunk.
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 27.03.26
 */
public interface AudioFileChunkConsumer extends AutoCloseable {

  void consume(AudioFileChunk audioFileChunk) throws StorageException;

  @Override
  void close() throws StorageException;
}
