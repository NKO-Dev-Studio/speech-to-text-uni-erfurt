package studio.nkodev.stt.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testsuite of {@link DbAccess}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class DbAccessTest {

  @TempDir private Path tempDir;

  @Test
  public void shouldInitializeDatabaseSuccessfully() throws SQLException {
    Path path = tempDir.resolve("notExisting.db");

    DbConnectionCoordinatorConfig config =
        new DbConnectionCoordinatorConfig() {
          @Override
          public Path getSqlFilePath() {
            return path;
          }

          @Override
          public int getMaximumPoolSize() {
            return 5;
          }

          @Override
          public int getMinimumIdleSize() {
            return 1;
          }

          @Override
          public int getConnectionTimeoutMs() {
            return 500;
          }
        };
    DbAccess dbAccess = new DbAccess(config);
    try (Connection connection = dbAccess.getConnection()) {
      int userVersion = readUserVersion(connection);
      assertEquals(1, userVersion);

      Set<String> tableNames = readTableNames(connection);
      assertEquals(1, tableNames.size());
      assertTrue(tableNames.contains("speech_to_text_task"));
    }
  }

  private static int readUserVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      ResultSet resultSet = statement.executeQuery("PRAGMA user_version;");

      if (resultSet.next()) {
        return resultSet.getInt(1);
      } else {
        throw new SQLException("Error during reading user_version");
      }
    }
  }

  private static Set<String> readTableNames(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      ResultSet resultSet =
          statement.executeQuery(
              """
                      SELECT name
                      FROM sqlite_schema
                      WHERE type = 'table'
                      """);
      Set<String> tableNames = new HashSet<>();
      while (resultSet.next()) {
        tableNames.add(resultSet.getString("name"));
      }

      return tableNames;
    }
  }
}
