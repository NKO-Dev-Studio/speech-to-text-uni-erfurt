package studio.nkodev.stt.api;

import java.time.Instant;

/**
 * Representation of a speech to text task
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public class SpeechToTextTask {

  private final long id;
  private final SpeechToTextTaskConfiguration configuration;
  private final Instant createdAt;
  private final Instant changedAt;

  public SpeechToTextTask(long id, SpeechToTextTaskConfiguration configuration, Instant createdAt, Instant changedAt) {
    this.id = id;
    this.configuration = configuration;
    this.createdAt = createdAt;
    this.changedAt = changedAt;
  }

  public long getId() {
    return id;
  }

  public SpeechToTextTaskConfiguration getConfiguration() {
    return configuration;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }


}
