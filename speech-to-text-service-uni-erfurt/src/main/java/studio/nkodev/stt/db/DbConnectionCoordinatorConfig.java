package studio.nkodev.stt.db;

import java.nio.file.Path;

/**
 * Configuration parameters op {@link DbAccess}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 20.03.26
 */
public interface DbConnectionCoordinatorConfig {
  Path getSqlFilePath();

  int getMaximumPoolSize();

  int getMinimumIdleSize();

  int getConnectionTimeoutMs();
}
