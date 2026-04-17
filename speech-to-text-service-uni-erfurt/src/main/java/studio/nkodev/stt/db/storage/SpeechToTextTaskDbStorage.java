package studio.nkodev.stt.db.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.db.DbAccess;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Implementation of a {@link SpeechToTextTaskStorage} using a database as persistent layer
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 20.03.26
 */
public class SpeechToTextTaskDbStorage implements SpeechToTextTaskStorage {

  private static final Logger logger = LoggerFactory.getLogger(SpeechToTextTaskDbStorage.class);

  private final DbAccess dbAccess;

  public SpeechToTextTaskDbStorage(DbAccess dbAccess) {
    this.dbAccess = dbAccess;
  }

  @Override
  public SpeechToTextTask getTask(long taskId) throws StorageException {
    logger.debug("Loading task having id {}", taskId);
    try (Connection connection = dbAccess.getConnection();
        PreparedStatement statement = connection.prepareStatement(createSelectTaskByIdSql())) {
      statement.setLong(1, taskId);
      ResultSet resultSet = statement.executeQuery();

      List<SpeechToTextTask> speechToTextTasks = mapResultSetToSpeechToTextTasks(resultSet);
      if (speechToTextTasks.isEmpty()) {
        throw new NotFoundStorageException("speech-to-text-task", taskId);
      }

      return speechToTextTasks.getFirst();
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public SpeechToTextTaskState getTaskState(long taskId) throws StorageException {
    logger.debug("Loading task state of task having id {}", taskId);
    try (Connection connection = dbAccess.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT state FROM speech_to_text_task WHERE id = ?")) {
      statement.setLong(1, taskId);
      ResultSet resultSet = statement.executeQuery();

      if (resultSet.next()) {
        return SpeechToTextTaskState.valueOf(resultSet.getString("state"));
      } else {
        throw new NotFoundStorageException("speech-to-text-task", taskId);
      }
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Stream<SpeechToTextTask> getTasks() throws StorageException {
    return getTasks(null);
  }

  @Override
  public Stream<SpeechToTextTask> getTasks(SpeechToTextTaskFilter filter) throws StorageException {
    logger.debug("Loading tasks matching filter {}", filter);
    int filterByStateCount =
        (filter != null && filter.states() != null) ? filter.states().size() : 0;

    boolean filterByLastChangeBeforeTimestampMillis =
        filter != null && filter.lastChangeBeforeTimestampMillis() != null;

    try (Connection connection = dbAccess.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                createSelectTasksSql(filterByStateCount, filterByLastChangeBeforeTimestampMillis))) {
      int parameterIndex = 1;
      if (filterByStateCount > 0) {
        for (SpeechToTextTaskState speechToTextTaskState : filter.states()) {
          statement.setString(parameterIndex++, speechToTextTaskState.toString());
        }
      }
      if (filterByLastChangeBeforeTimestampMillis) {
        statement.setLong(parameterIndex, filter.lastChangeBeforeTimestampMillis());
      }

      ResultSet resultSet = statement.executeQuery();
      return mapResultSetToSpeechToTextTasks(resultSet).stream();
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<SpeechToTextTask> claimNextPendingTask() throws StorageException {
    logger.trace("Claiming next pending task");
    try (Connection connection = dbAccess.getConnection()) {
      connection.setAutoCommit(false);
      try {
        Optional<Long> nextPendingTaskId =
            findNextTaskId(connection, SpeechToTextTaskState.PENDING);
        if (nextPendingTaskId.isEmpty()) {
          connection.commit();
          return Optional.empty();
        }

        int updatedRows =
            updateTaskState(connection, nextPendingTaskId.get(), SpeechToTextTaskState.RUNNING);
        if (updatedRows == 0) {
          connection.rollback();
          return Optional.empty();
        }

        SpeechToTextTask task = getTask(connection, nextPendingTaskId.get());
        connection.commit();
        return Optional.of(task);
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public SpeechToTextTask createTask(SpeechToTextTaskConfiguration taskConfiguration)
      throws StorageException {
    logger.debug("Creating task having configuration {}", taskConfiguration);
    Instant now = Instant.now();

    try (Connection connection = dbAccess.getConnection()) {
      connection.setAutoCommit(false);
      try {
        long taskId = insertTask(connection, taskConfiguration, now);
        connection.commit();
        return new SpeechToTextTask(taskId, taskConfiguration, now, null);
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void updateTaskState(long taskId, SpeechToTextTaskState taskState)
      throws StorageException {
    logger.debug("Updating task state of task having id {} to {}", taskId, taskState);
    Instant now = Instant.now();
    Set<SpeechToTextTaskState> allowedPreviousTaskStates =
        taskState.getAllowedPreviousSpeechToTextTaskStates();

    if (allowedPreviousTaskStates.isEmpty()) {
      throw new IllegalStateException(
          "Task state " + taskState + " does not allow transitions from any previous state");
    }

    try (Connection connection = dbAccess.getConnection()) {
      int updatedRows = updateTaskState(connection, taskId, taskState);
      if (!taskExists(connection, taskId)) {
        throw new NotFoundStorageException("speech-to-text-task", taskId);
      }
      if (updatedRows == 0) {
        SpeechToTextTaskState currentTaskState = getTaskState(taskId);
        throw new StorageException(
            "Invalid speech-to-text-task state transition from "
                + currentTaskState
                + " to "
                + taskState);
      }
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void deleteTask(long taskId) throws StorageException {
    logger.debug("Deleting task having id {}", taskId);

    String deleteSql = createDeleteTaskSql();
    try (Connection connection = dbAccess.getConnection()) {
      PreparedStatement statement = connection.prepareStatement(deleteSql);
      statement.setLong(1, taskId);

      statement.executeUpdate();
    } catch (Exception e) {
      throw new StorageException(e);
    }
  }

  private static List<SpeechToTextTask> mapResultSetToSpeechToTextTasks(ResultSet resultSet)
      throws SQLException {
    List<SpeechToTextTask> speechToTextTasks = new ArrayList<>();

    while (resultSet.next()) {
      long id = resultSet.getLong("id");
      String engineIdentifier = resultSet.getString("engine_type");
      String modelIdentifier = resultSet.getString("model_identifier");
      SpeechToTextEngineOutputFormat outputFormat =
          SpeechToTextEngineOutputFormat.valueOf(resultSet.getString("output_format"));
      String localeString = resultSet.getString("locale");
      Locale locale = localeString != null ? Locale.forLanguageTag(localeString) : null;
      Instant createdAt = Instant.ofEpochMilli(resultSet.getLong("created_at"));
      long changedAtEpochMillis = resultSet.getLong("changed_at");
      Instant changedAt = resultSet.wasNull() ? null : Instant.ofEpochMilli(changedAtEpochMillis);

      SpeechToTextTaskConfiguration configuration =
          new SpeechToTextTaskConfiguration(
              engineIdentifier, locale, modelIdentifier, outputFormat);
      SpeechToTextTask speechToTextTask =
          new SpeechToTextTask(id, configuration, createdAt, changedAt);
      speechToTextTasks.add(speechToTextTask);
    }
    return speechToTextTasks;
  }

  private static String createSelectTasksSql(
      int filterByStateCount, boolean filterByLastChangeBeforeTimestampMillis) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              task.id,
              task.engine_type,
              task.model_identifier,
              task.output_format,
              task.locale,
              task.created_at,
              task.changed_at
            FROM speech_to_text_task task
            """);
    if (filterByStateCount > 0 || filterByLastChangeBeforeTimestampMillis) {
      sql.append(" WHERE");

      if (filterByStateCount > 0) {
        StringJoiner statesJoiner = new StringJoiner(", ");
        for (int index = 0; index < filterByStateCount; index++) {
          statesJoiner.add("?");
        }

        sql.append(" task.state in (").append(statesJoiner).append(")");

        if (filterByLastChangeBeforeTimestampMillis) {
          sql.append(" AND");
        }
      }

      if (filterByLastChangeBeforeTimestampMillis) {
        sql.append(" task.changed_at < ?");
      }
    }
    sql.append(" ORDER BY task.id");
    return sql.toString();
  }

  private static String createSelectTaskByIdSql() {
    return """
        SELECT
          task.id,
          task.engine_type,
          task.model_identifier,
          task.output_format,
          task.locale,
          task.created_at,
          task.changed_at
        FROM speech_to_text_task task
        WHERE task.id = ?
        ORDER BY task.id
        """;
  }

  private static long insertTask(
      Connection connection, SpeechToTextTaskConfiguration taskConfiguration, Instant createdAt)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO speech_to_text_task (state, engine_type, model_identifier, output_format, locale, created_at, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            PreparedStatement.RETURN_GENERATED_KEYS)) {
      statement.setString(1, SpeechToTextTaskState.WAITING_FOR_AUDIO.name());
      statement.setString(2, taskConfiguration.engineIdentifier());
      statement.setString(3, taskConfiguration.modelIdentifier());
      statement.setString(4, taskConfiguration.outputFormat().name());
      statement.setString(
          5,
          taskConfiguration.locale() != null ? taskConfiguration.locale().toLanguageTag() : null);
      statement.setLong(6, createdAt.toEpochMilli());
      statement.setNull(7, java.sql.Types.BIGINT);
      statement.executeUpdate();

      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        if (generatedKeys.next()) {
          return generatedKeys.getLong(1);
        }
      }
    }

    throw new SQLException("Could not determine id of inserted speech-to-text-task");
  }

  private static String createDeleteTaskSql() {
    return "DELETE FROM speech_to_text_task WHERE id = ?";
  }

  private static String createUpdateTaskStateSql(int allowedPreviousTaskStateCount) {
    StringJoiner allowedPreviousStatesPlaceholderJoiner = new StringJoiner(", ");
    for (int index = 0; index < allowedPreviousTaskStateCount; index++) {
      allowedPreviousStatesPlaceholderJoiner.add("?");
    }

    return """
        UPDATE speech_to_text_task
        SET state = ?, changed_at = ?
        WHERE id = ? AND state IN (%s)
        """
        .formatted(allowedPreviousStatesPlaceholderJoiner);
  }

  private static Optional<Long> findNextTaskId(
      Connection connection, SpeechToTextTaskState taskState) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            SELECT id
            FROM speech_to_text_task
            WHERE state = ?
            ORDER BY id
            LIMIT 1
            """)) {
      statement.setString(1, taskState.name());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return Optional.of(resultSet.getLong("id"));
        }

        return Optional.empty();
      }
    }
  }

  private static SpeechToTextTask getTask(Connection connection, long taskId)
      throws SQLException, StorageException {
    try (PreparedStatement statement = connection.prepareStatement(createSelectTaskByIdSql())) {
      statement.setLong(1, taskId);
      List<SpeechToTextTask> tasks = mapResultSetToSpeechToTextTasks(statement.executeQuery());
      if (tasks.isEmpty()) {
        throw new NotFoundStorageException("speech-to-text-task", taskId);
      }

      return tasks.getFirst();
    }
  }

  private static int updateTaskState(
      Connection connection, long taskId, SpeechToTextTaskState taskState) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            createUpdateTaskStateSql(
                taskState.getAllowedPreviousSpeechToTextTaskStates().size()))) {
      statement.setString(1, taskState.name());
      statement.setLong(2, System.currentTimeMillis());
      statement.setLong(3, taskId);
      int parameterIndex = 4;
      for (SpeechToTextTaskState allowedPreviousTaskState :
          taskState.getAllowedPreviousSpeechToTextTaskStates()) {
        statement.setString(parameterIndex++, allowedPreviousTaskState.name());
      }

      return statement.executeUpdate();
    }
  }

  private static boolean taskExists(Connection connection, long taskId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM speech_to_text_task WHERE id = ?")) {
      statement.setLong(1, taskId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
