package studio.nkodev.stt.db.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.nkodev.stt.api.SpeechToTextTask;
import studio.nkodev.stt.api.SpeechToTextTaskConfiguration;
import studio.nkodev.stt.api.SpeechToTextTaskState;
import studio.nkodev.stt.db.DbAccess;
import studio.nkodev.stt.db.DbConnectionCoordinatorConfig;
import studio.nkodev.stt.engine.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.engine.api.SpeechToTextEngineType;
import studio.nkodev.stt.storage.SpeechToTextTaskStorage.SpeechToTextTaskFilter;
import studio.nkodev.stt.storage.exception.NotFoundStorageException;
import studio.nkodev.stt.storage.exception.StorageException;

/**
 * Testsuite of {@link SpeechToTextTaskDbStorage}
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 06.04.26
 */
public class SpeechToTextTaskDbStorageTest {

  private static final String RESOURCE_BASE_PATH = "/studio/nkodev/stt/db/storage/";

  @TempDir private Path tempDir;

  @Test
  public void shouldLoadTaskSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("get-task-success.sql");

    SpeechToTextTask task = storage.getTask(11L);

    assertTask(
        task,
        11L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.GERMAN,
            "base",
            SpeechToTextEngineOutputFormat.SRT),
        Instant.ofEpochMilli(1710000000000L),
        Instant.ofEpochMilli(1710000100000L));
  }

  @Test
  public void shouldThrowNotFoundWhenLoadingUnknownTask() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage();

    assertThrows(NotFoundStorageException.class, () -> storage.getTask(999L));
  }

  @Test
  public void shouldWrapConnectionErrorWhenLoadingTask() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception = assertThrows(StorageException.class, () -> storage.getTask(11L));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldLoadTaskStateSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("get-task-state-success.sql");

    SpeechToTextTaskState taskState = storage.getTaskState(12L);

    assertEquals(SpeechToTextTaskState.FAILED, taskState);
  }

  @Test
  public void shouldThrowNotFoundWhenLoadingStateOfUnknownTask() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage();

    assertThrows(NotFoundStorageException.class, () -> storage.getTaskState(999L));
  }

  @Test
  public void shouldWrapConnectionErrorWhenLoadingTaskState() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(StorageException.class, () -> storage.getTaskState(12L));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldLoadAllTasksSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("get-tasks-success.sql");

    List<SpeechToTextTask> tasks = storage.getTasks().toList();

    assertEquals(3, tasks.size());
    assertTask(
        tasks.get(0),
        21L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.GERMAN,
            "tiny",
            SpeechToTextEngineOutputFormat.JSON),
        Instant.ofEpochMilli(1710000200000L),
        null);
    assertTask(
        tasks.get(1),
        22L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.US,
            "base",
            SpeechToTextEngineOutputFormat.TXT),
        Instant.ofEpochMilli(1710000300000L),
        Instant.ofEpochMilli(1710000400000L));
    assertTask(
        tasks.get(2),
        23L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            null,
            "large-v3",
            SpeechToTextEngineOutputFormat.SRT),
        Instant.ofEpochMilli(1710000500000L),
        Instant.ofEpochMilli(1710000600000L));
  }

  @Test
  public void shouldReturnNoTasksWhenLoadingAllTasksFromEmptyStorage() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage();

    List<SpeechToTextTask> tasks = storage.getTasks().toList();

    assertTrue(tasks.isEmpty());
  }

  @Test
  public void shouldWrapConnectionErrorWhenLoadingAllTasks() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception = assertThrows(StorageException.class, () -> storage.getTasks());

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldLoadFilteredTasksSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("get-tasks-filter-success.sql");
    SpeechToTextTaskFilter filter =
        SpeechToTextTaskFilter.builder()
            .states(List.of(SpeechToTextTaskState.PENDING, SpeechToTextTaskState.FAILED))
            .lastChangeBeforeTimestampMillis(1710000800000L)
            .build();

    List<SpeechToTextTask> tasks = storage.getTasks(filter).toList();

    assertEquals(2, tasks.size());
    assertEquals(32L, tasks.get(0).getId());
    assertEquals(33L, tasks.get(1).getId());
  }

  @Test
  public void shouldReturnNoTasksWhenNoTaskMatchesFilter() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("get-tasks-filter-no-match.sql");
    SpeechToTextTaskFilter filter =
        SpeechToTextTaskFilter.builder()
            .states(List.of(SpeechToTextTaskState.RUNNING))
            .lastChangeBeforeTimestampMillis(1710001000000L)
            .build();

    List<SpeechToTextTask> tasks = storage.getTasks(filter).toList();

    assertTrue(tasks.isEmpty());
  }

  @Test
  public void shouldWrapConnectionErrorWhenLoadingFilteredTasks() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(
            StorageException.class,
            () ->
                storage.getTasks(
                    SpeechToTextTaskFilter.builder()
                        .states(List.of(SpeechToTextTaskState.PENDING))
                        .build()));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldClaimNextPendingTaskSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("claim-next-pending-task-success.sql");

    Optional<SpeechToTextTask> claimedTask = storage.claimNextPendingTask();

    assertTrue(claimedTask.isPresent());
    assertTask(
        claimedTask.get(),
        51L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.GERMAN,
            "base",
            SpeechToTextEngineOutputFormat.SRT),
        Instant.ofEpochMilli(1710001100000L),
        claimedTask.get().getChangedAt());
    assertNotNull(claimedTask.get().getChangedAt());
    assertEquals(SpeechToTextTaskState.RUNNING, storage.getTaskState(51L));
    assertEquals(SpeechToTextTaskState.PENDING, storage.getTaskState(52L));
  }

  @Test
  public void shouldReturnEmptyWhenNoPendingTaskCanBeClaimed() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("claim-next-pending-task-no-pending.sql");

    Optional<SpeechToTextTask> claimedTask = storage.claimNextPendingTask();

    assertTrue(claimedTask.isEmpty());
  }

  @Test
  public void shouldWrapConnectionErrorWhenClaimingNextPendingTask() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    doThrow(new SQLException("connection error")).when(connection).setAutoCommit(false);
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(StorageException.class, storage::claimNextPendingTask);

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldCreateTaskSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage();
    SpeechToTextTaskConfiguration taskConfiguration =
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.CANADA_FRENCH,
            "medium",
            SpeechToTextEngineOutputFormat.JSON);

    SpeechToTextTask createdTask = storage.createTask(taskConfiguration);

    assertTrue(createdTask.getId() > 0);
    assertEquals(taskConfiguration, createdTask.getConfiguration());
    assertNotNull(createdTask.getCreatedAt());
    assertNull(createdTask.getChangedAt());

    SpeechToTextTask persistedTask = storage.getTask(createdTask.getId());
    assertTask(
        persistedTask,
        createdTask.getId(),
        taskConfiguration,
        createdTask.getCreatedAt().truncatedTo(ChronoUnit.MILLIS),
        null);
    assertEquals(SpeechToTextTaskState.WAITING_FOR_AUDIO, storage.getTaskState(createdTask.getId()));
  }

  @Test
  public void shouldFailWhenCreatingTaskWithoutConfiguration() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage();

    StorageException exception = assertThrows(StorageException.class, () -> storage.createTask(null));

    assertInstanceOf(NullPointerException.class, exception.getCause());
  }

  @Test
  public void shouldWrapConnectionErrorWhenCreatingTask() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    doThrow(new SQLException("connection error")).when(connection).setAutoCommit(false);
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(
            StorageException.class,
            () ->
                storage.createTask(
                    new SpeechToTextTaskConfiguration(
                        SpeechToTextEngineType.WHISPER_LOCAL,
                        Locale.GERMAN,
                        "base",
                        SpeechToTextEngineOutputFormat.SRT)));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldUpdateTaskStateSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("update-task-state-success.sql");

    storage.updateTaskState(71L, SpeechToTextTaskState.PENDING);

    assertEquals(SpeechToTextTaskState.PENDING, storage.getTaskState(71L));
    SpeechToTextTask task = storage.getTask(71L);
    assertNotNull(task.getChangedAt());
  }

  @Test
  public void shouldFailWhenUpdatingTaskStateWithInvalidTransition() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("update-task-state-invalid-transition.sql");

    StorageException exception =
        assertThrows(
            StorageException.class,
            () -> storage.updateTaskState(72L, SpeechToTextTaskState.COMPLETED));

    assertEquals(
        "Invalid speech-to-text-task state transition from WAITING_FOR_AUDIO to COMPLETED",
        exception.getMessage());
    assertEquals(SpeechToTextTaskState.WAITING_FOR_AUDIO, storage.getTaskState(72L));
  }

  @Test
  public void shouldWrapConnectionErrorWhenUpdatingTaskState() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(
            StorageException.class,
            () -> storage.updateTaskState(71L, SpeechToTextTaskState.PENDING));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  @Test
  public void shouldDeleteTaskSuccessfully() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("delete-task-success.sql");

    storage.deleteTask(81L);

    assertThrows(NotFoundStorageException.class, () -> storage.getTask(81L));
    assertTask(
        storage.getTask(82L),
        82L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.US,
            "small",
            SpeechToTextEngineOutputFormat.TXT),
        Instant.ofEpochMilli(1710001800000L),
        Instant.ofEpochMilli(1710001900000L));
  }

  @Test
  public void shouldIgnoreDeletingUnknownTask() throws Exception {
    SpeechToTextTaskDbStorage storage = createStorage("delete-task-unknown.sql");

    storage.deleteTask(999L);

    assertTask(
        storage.getTask(91L),
        91L,
        new SpeechToTextTaskConfiguration(
            SpeechToTextEngineType.WHISPER_LOCAL,
            Locale.GERMAN,
            "base",
            SpeechToTextEngineOutputFormat.SRT),
        Instant.ofEpochMilli(1710002000000L),
        null);
  }

  @Test
  public void shouldWrapConnectionErrorWhenDeletingTask() throws Exception {
    DbAccess dbAccess = mock(DbAccess.class);
    Connection connection = mock(Connection.class);
    when(dbAccess.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection error"));
    SpeechToTextTaskDbStorage storage = new SpeechToTextTaskDbStorage(dbAccess);

    StorageException exception =
        assertThrows(StorageException.class, () -> storage.deleteTask(81L));

    assertInstanceOf(SQLException.class, exception.getCause());
  }

  private SpeechToTextTaskDbStorage createStorage() throws Exception {
    DbAccess dbAccess = createDbAccess();
    return new SpeechToTextTaskDbStorage(dbAccess);
  }

  private SpeechToTextTaskDbStorage createStorage(String sqlScriptResourceName) throws Exception {
    DbAccess dbAccess = createDbAccess();
    executeSqlScript(dbAccess, sqlScriptResourceName);
    return new SpeechToTextTaskDbStorage(dbAccess);
  }

  private DbAccess createDbAccess() throws SQLException {
    Path dbPath = tempDir.resolve(UUID.randomUUID() + ".sqlite");
    return new DbAccess(
        new DbConnectionCoordinatorConfig() {
          @Override
          public Path getSqlFilePath() {
            return dbPath;
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
        });
  }

  private void executeSqlScript(DbAccess dbAccess, String sqlScriptResourceName)
      throws SQLException, IOException {
    try (Connection connection = dbAccess.getConnection();
        Statement statement = connection.createStatement()) {
      for (String sqlStatement : readSqlStatements(sqlScriptResourceName)) {
        statement.execute(sqlStatement);
      }
    }
  }

  private List<String> readSqlStatements(String sqlScriptResourceName) throws IOException {
    try (InputStream inputStream =
        SpeechToTextTaskDbStorageTest.class.getResourceAsStream(
            RESOURCE_BASE_PATH + sqlScriptResourceName)) {
      if (inputStream == null) {
        throw new IOException("Could not load sql script " + sqlScriptResourceName);
      }

      String script = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return List.of(script.split(";"))
          .stream()
          .map(String::trim)
          .filter(statement -> !statement.isEmpty())
          .toList();
    }
  }

  private static void assertTask(
      SpeechToTextTask task,
      long expectedId,
      SpeechToTextTaskConfiguration expectedConfiguration,
      Instant expectedCreatedAt,
      Instant expectedChangedAt) {
    assertEquals(expectedId, task.getId());
    assertEquals(expectedConfiguration, task.getConfiguration());
    assertEquals(expectedCreatedAt, task.getCreatedAt());
    assertEquals(expectedChangedAt, task.getChangedAt());
  }
}
