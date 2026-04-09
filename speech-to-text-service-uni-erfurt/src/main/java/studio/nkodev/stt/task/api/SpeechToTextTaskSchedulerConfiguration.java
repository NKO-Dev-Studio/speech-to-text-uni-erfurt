package studio.nkodev.stt.task.api;

import studio.nkodev.stt.task.SpeechToTextTaskScheduler;

/**
 * Configuration of the {@link SpeechToTextTaskScheduler @}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 09.03.26
 */
public interface SpeechToTextTaskSchedulerConfiguration {
  int getNumberOfParallelTasks();
}
