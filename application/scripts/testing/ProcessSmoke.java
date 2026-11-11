import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Run after verify: java scripts/testing/ProcessSmoke.java [absolute application directory]. */
public class ProcessSmoke {
  static Path root, work, evidence;
  static String javaExecutable;

  static Process start(String module, String log, String... args) throws Exception {
    List<String> command =
        new ArrayList<>(
            List.of(
                javaExecutable,
                "-jar",
                root.resolve(module + "/target/" + module + "-1.0.0-SNAPSHOT.jar").toString()));
    command.addAll(List.of(args));
    return new ProcessBuilder(command)
        .directory(work.toFile())
        .redirectErrorStream(true)
        .redirectOutput(evidence.resolve(log).toFile())
        .start();
  }

  static void await(Process p) throws Exception {
    if (!p.waitFor(25, TimeUnit.SECONDS)) {
      p.destroyForcibly();
      throw new IllegalStateException("Process timed out");
    }
    if (p.exitValue() != 0) throw new IllegalStateException("Process failed: " + p.exitValue());
  }

  static void check(String log, String text) throws Exception {
    if (!Files.readString(evidence.resolve(log)).contains(text))
      throw new IllegalStateException(log + " missing " + text);
  }

  public static void main(String[] args) throws Exception {
    root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
    work = root.resolve(".local/testing/process-smoke-" + UUID.randomUUID());
    Files.createDirectories(work);
    evidence = root.getParent().resolve("docs/verification/evidence/process-smoke");
    Files.createDirectories(evidence);
    javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    Files.createDirectories(work.resolve("config"));
    Files.writeString(
        work.resolve("config/server.properties"),
        "server.host=127.0.0.1\nserver.port=0\ndatabase.path=demo.db\nserver.maxConnections=32\n");
    await(start("server", "seed.log", "--seed"));
    Process server = start("server", "server.log");
    try {
      int port = -1;
      for (int i = 0; i < 100; i++) {
        String log = Files.readString(evidence.resolve("server.log"));
        var matcher =
            java.util.regex.Pattern.compile("TCP server listening on 127\\.0\\.0\\.1:(\\d+)")
                .matcher(log);
        if (matcher.find()) {
          port = Integer.parseInt(matcher.group(1));
          break;
        }
        if (!server.isAlive()) throw new IllegalStateException("Server exited");
        Thread.sleep(100);
      }
      if (port < 0) throw new IllegalStateException("Server not ready");
      String[] users = {"citizen1", "operator", "fire1"};
      List<Process> clients = new ArrayList<>();
      for (String user : users) {
        Process client = start("client", user + ".log");
        clients.add(client);
        String input =
            "127.0.0.1\n"
                + port
                + "\n2\n"
                + user
                + "\nDemoPass123!\n1\n2\n"
                + (user.equals("operator") ? "3\n4\n" : user.equals("fire1") ? "3\n" : "")
                + "9\n0\n";
        try (var writer = client.outputWriter(StandardCharsets.UTF_8)) {
          writer.write(input);
        }
      }
      for (Process client : clients) await(client);
      check("citizen1.log", "CITIZEN");
      check("operator.log", "OPERATOR");
      check("fire1.log", "RESPONDER");
      for (String user : users) {
        check(user + ".log", "PONG");
        check(user + ".log", "Logged out");
      }
      check("operator.log", "TEST_NOTIFICATION");
      check("fire1.log", "fire1 simulated team");
      System.out.println(
          "PASS: seed + server JVM + three real console client JVMs; all roles, PONG, logout, team"
              + " listing and private event.");
      System.out.println("Evidence: " + evidence);
    } finally {
      server.destroy();
      if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly();
    }
  }
}
