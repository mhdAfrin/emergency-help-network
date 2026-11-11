package lk.emergency.server;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import lk.emergency.common.Validation;

public final class ServerMain {
  public static void main(String[] args) throws Exception {
    System.setOut(
        new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(
        new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    System.out.println(Validation.DISCLAIMER);
    Properties config = new Properties();
    Path configPath = Path.of("config/server.properties");
    // Accept the original local configuration when upgrading an existing checkout.
    if (!Files.exists(configPath)) configPath = Path.of("server.properties");
    if (Files.exists(configPath))
      try (InputStream in = Files.newInputStream(configPath)) {
        config.load(in);
      }
    Path dbPath = Path.of(config.getProperty("database.path", ".local/data/emergency.db"));
    try (Database db = new Database(dbPath)) {
      AuthService auth = new AuthService(db);
      if (Arrays.asList(args).contains("--seed")) {
        auth.seed();
        System.out.println("Demo accounts seeded without resetting existing passwords or teams.");
        return;
      }
      try (TcpServer server =
          new TcpServer(
              config.getProperty("server.host", "127.0.0.1"),
              Integer.parseInt(config.getProperty("server.port", "5050")),
              Integer.parseInt(config.getProperty("server.maxConnections", "32")),
              db,
              auth)) {
        Thread shutdown =
            new Thread(
                () -> {
                  server.close();
                  db.close();
                },
                "shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        System.out.println(
            "TCP server listening on "
                + config.getProperty("server.host", "127.0.0.1")
                + ":"
                + server.port());
        try {
          server.await();
        } finally {
          try {
            Runtime.getRuntime().removeShutdownHook(shutdown);
          } catch (IllegalStateException ignored) {
          }
        }
      }
    }
  }
}
