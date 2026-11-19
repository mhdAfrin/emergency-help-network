package lk.emergency.server;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import lk.emergency.common.Validation;
import lk.emergency.server.config.ServerSettings;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;

public final class ServerMain {
  public static void main(String[] args) throws Exception {
    System.setOut(
        new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(
        new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    System.out.println(Validation.DISCLAIMER);
    if (Arrays.asList(args).contains("--help")) {
      System.out.println(ServerSettings.HELP);
      return;
    }
    ServerSettings config = ServerSettings.load(args);
    try (Database db = new Database(config.database())) {
      AuthService auth = new AuthService(db);
      if (config.seed()) {
        auth.seed();
        System.out.println("Demo accounts seeded without resetting existing passwords or teams.");
        return;
      }
      try (TcpServer server =
          new TcpServer(
              config.host(),
              config.port(),
              config.maxConnections(),
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
                + config.host()
                + ":"
                + server.port());
        System.out.println("Database: " + config.database().toAbsolutePath().normalize());
        if (config.host().equals("0.0.0.0"))
          System.out.println("LAN mode: clients must use this laptop's Wi-Fi IPv4 address, port " + server.port() + ".");
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
