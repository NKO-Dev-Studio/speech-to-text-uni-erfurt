package studio.nkodev.stt.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the connection and initialization of sqllite instance
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 20.03.26
 */
public class DbAccess {

  private static final Logger logger = LoggerFactory.getLogger(DbAccess.class);

  private final DataSource dataSource;

  public DbAccess(DbConnectionCoordinatorConfig config) throws SQLException {
    this.dataSource = initializeDataSource(config);
    initializeSchema(dataSource);
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  private static DataSource initializeDataSource(DbConnectionCoordinatorConfig config) {
    logger.debug("Initialize hikari data source");
    String jdbcUrl = "jdbc:sqlite:" + config.getSqlFilePath();

    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(jdbcUrl);
    hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
    hikariConfig.setMinimumIdle(config.getMinimumIdleSize());
    hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
    hikariConfig.setAutoCommit(true);

    return new HikariDataSource(hikariConfig);
  }

  private static void initializeSchema(DataSource dataSource) throws SQLException {
    logger.debug("Initialize schema if necessary");
    int userVersion = readUserVersion(dataSource);
    if (userVersion == 0) {
      logger.info("Found empty database file. Initialize schema");
      executeSchemaVersion1Script(dataSource);
    }
  }

  private static int readUserVersion(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        ResultSet resultSet = statement.executeQuery("PRAGMA user_version;");

        if (resultSet.next()) {
          return resultSet.getInt(1);
        } else {
          throw new SQLException("Error during reading user_version");
        }
      }
    }
  }

  private static void executeSchemaVersion1Script(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("PRAGMA user_version = 1");
        statement.execute("PRAGMA foreign_keys = ON;");
        statement.execute(
            """
                    CREATE TABLE IF NOT EXISTS speech_to_text_task
                    (
                        id               INTEGER PRIMARY KEY,
                        state            TEXT NOT NULL,
                        engine_type      TEXT NOT NULL,
                        model_identifier TEXT NOT NULL,
                        output_format    TEXT NOT NULL,
                        locale           TEXT NULL,
                        created_at       INT NOT NULL DEFAULT (unixepoch()),
                        changed_at       INT NULL
                    );
                    """);
      }
    }
  }
}
