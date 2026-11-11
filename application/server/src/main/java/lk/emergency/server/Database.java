package lk.emergency.server;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;

public final class Database implements AutoCloseable {
  @FunctionalInterface
  public interface Work<T> {
    T run(Connection connection) throws Exception;
  }

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "database"));
  private Connection connection;
  private final java.util.concurrent.atomic.AtomicBoolean closed =
      new java.util.concurrent.atomic.AtomicBoolean();

  public Database(Path path) {
    try {
      Path parent = path.toAbsolutePath().getParent();
      Files.createDirectories(parent);
      call(
          c -> {
            connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            try (Statement s = connection.createStatement()) {
              s.execute("PRAGMA foreign_keys=ON");
              s.execute("PRAGMA busy_timeout=5000");
              s.execute("PRAGMA journal_mode=WAL");
            }
            migrate();
            return null;
          });
    } catch (RuntimeException | IOException e) {
      executor.shutdownNow();
      throw new IllegalStateException("Database startup failed", e);
    }
  }

  private void migrate() throws Exception {
    try (Statement s = connection.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at"
              + " TEXT NOT NULL)");
    }
    boolean applied;
    try (Statement s = connection.createStatement();
        ResultSet rs = s.executeQuery("SELECT 1 FROM schema_migrations WHERE version=1")) {
      applied = rs.next();
    }
    if (applied) return;
    connection.setAutoCommit(false);
    try (InputStream stream = Database.class.getResourceAsStream("/db/V001__foundation.sql")) {
      if (stream == null) throw new IOException("Missing migration");
      String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      for (String command : sql.split(";"))
        if (!command.isBlank())
          try (Statement s = connection.createStatement()) {
            s.execute(command);
          }
      try (PreparedStatement p =
          connection.prepareStatement("INSERT INTO schema_migrations VALUES(1,?)")) {
        p.setString(1, java.time.Instant.now().toString());
        p.executeUpdate();
      }
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  public <T> T call(Work<T> work) {
    try {
      return executor.submit(() -> work.run(connection)).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Database wait interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException r) throw r;
      throw new IllegalStateException("Database operation failed", e.getCause());
    }
  }

  public <T> T transaction(Work<T> work) {
    return call(
        c -> {
          c.setAutoCommit(false);
          try {
            T result = work.run(c);
            c.commit();
            return result;
          } catch (Exception e) {
            c.rollback();
            throw e;
          } finally {
            c.setAutoCommit(true);
          }
        });
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      call(
          c -> {
            if (c != null) c.close();
            return null;
          });
    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
