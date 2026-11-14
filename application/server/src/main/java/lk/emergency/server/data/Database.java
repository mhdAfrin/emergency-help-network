package lk.emergency.server.data;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;

public final class Database implements AutoCloseable {
  @FunctionalInterface
  public interface Work<T> {
    T run(Connection connection) throws Exception;
  }

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "database"));
  private Connection connection;
  private final java.util.function.LongConsumer commitObserver;
  private final java.util.concurrent.atomic.AtomicBoolean closed =
      new java.util.concurrent.atomic.AtomicBoolean();

  public Database(Path path) {
    this(path, time -> {});
  }

  public Database(Path path, java.util.function.LongConsumer commitObserver) {
    this.commitObserver = commitObserver;
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
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at"
              + " TEXT NOT NULL)");
    }
    String[] migrations = {"V001__foundation.sql", "V002__short_references.sql"};
    for (int index = 0; index < migrations.length; index++) {
      int version = index + 1;
      try (var query =
          connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=?")) {
        query.setInt(1, version);
        try (var rows = query.executeQuery()) {
          if (rows.next()) continue;
        }
      }
      connection.setAutoCommit(false);
      try (InputStream stream = Database.class.getResourceAsStream("/db/" + migrations[index])) {
        if (stream == null) throw new IOException("Missing migration: " + migrations[index]);
        for (String command :
            new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";")) {
          if (!command.isBlank())
            try (var statement = connection.createStatement()) {
              statement.execute(command);
            }
        }
        try (var insert =
            connection.prepareStatement("INSERT INTO schema_migrations VALUES(?,?)")) {
          insert.setInt(1, version);
          insert.setString(2, java.time.Instant.now().toString());
          insert.executeUpdate();
        }
        connection.commit();
      } catch (Exception failure) {
        connection.rollback();
        throw failure;
      } finally {
        connection.setAutoCommit(true);
      }
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
            long committedAt = System.nanoTime();
            try {
              commitObserver.accept(committedAt);
            } catch (RuntimeException ignored) {
            }
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
