import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Runs actual console clients and server in separate JVMs using an isolated fictional database. */
public class ProcessSmoke {
  static Path root, work, evidence;
  static String javaExecutable;
  static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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

  static String log(String name) throws IOException {
    Path path = evidence.resolve(name);
    return Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "";
  }

  static String await(String file, String text) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      String current = log(file);
      if (current.contains(text)) return current;
      Thread.sleep(40);
    }
    throw new IllegalStateException(file + " did not contain " + text + "; inspect " + evidence);
  }

  static String lastMatch(String expression, String text) {
    Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(text);
    String result = null;
    while (matcher.find()) result = matcher.group(1);
    if (result == null) throw new IllegalStateException("Pattern not found: " + expression);
    return result;
  }

  static void finished(Process process) throws Exception {
    if (!process.waitFor(25, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("Process timed out");
    }
    if (process.exitValue() != 0)
      throw new IllegalStateException("Process exit " + process.exitValue());
  }

  static class Client implements AutoCloseable {
    final String name;
    final Process process;
    final Writer input;

    Client(String name, int port, String role) throws Exception {
      this.name = name;
      process = start("client", name + ".log");
      input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
      send("127.0.0.1\n" + port + "\n2\n" + name + "\nDemoPass123!\n");
      await(name + ".log", "[" + role + "]");
    }

    void send(String text) throws IOException {
      input.write(text);
      input.flush();
    }

    String waitFor(String text) throws Exception {
      return await(name + ".log", text);
    }

    public void close() throws Exception {
      if (process.isAlive())
        try {
          send("9\n0\n");
          input.close();
          finished(process);
        } catch (Exception e) {
          process.destroyForcibly();
          throw e;
        }
    }
  }

  public static void main(String[] args) throws Exception {
    root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
    work = root.resolve(".local/testing/member2-smoke-" + UUID.randomUUID());
    Files.createDirectories(work.resolve("config"));
    evidence = root.getParent().resolve("docs/verification/evidence/member2-fixed/process-smoke");
    Files.createDirectories(evidence);
    javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    Files.writeString(
        work.resolve("config/server.properties"),
        "server.host=127.0.0.1\nserver.port=0\nserver.maxConnections=32\ndatabase.path=demo.db\n");
    finished(start("server", "seed.log", "--seed"));
    if (!Files.exists(work.resolve("demo.db")))
      throw new IllegalStateException("Configured database was not seeded");
    Process server = start("server", "server.log");
    try {
      String serverLog = await("server.log", "TCP server listening on");
      int port =
          Integer.parseInt(lastMatch("TCP server listening on 127\\.0\\.0\\.1:(\\d+)", serverLog));
      try (Client operator = new Client("operator", port, "OPERATOR");
          Client citizen = new Client("citizen1", port, "CITIZEN");
          Client police = new Client("police1", port, "RESPONDER");
          Client ambulance = new Client("ambulance1", port, "RESPONDER");
          Client fire = new Client("fire1", port, "RESPONDER");
          Client replacement = new Client("fire2", port, "RESPONDER")) {
        citizen.send("2\n");
        citizen.waitFor("PONG");
        operator.send("3\n\n\n\n");
        String teams = operator.waitFor("police1 simulated team");
        String policeId = teamId(teams, "police1"),
            ambulanceId = teamId(teams, "ambulance1"),
            fireId = teamId(teams, "fire1"),
            replacementId = teamId(teams, "fire2");
        citizen.send(
            "3\nROAD_ACCIDENT\nFictional campus gate\nAcademic accident demonstration\n\n\n");
        String id =
            lastMatch(
                "Confirmed incident reference: (" + UUID_PATTERN + ")",
                citizen.waitFor("Confirmed incident reference:"));
        operator.waitFor("INCIDENT_CREATED");
        operator.send("7\n" + id + "\nHIGH\ny\n");
        operator.waitFor("\"priority\" : \"HIGH\"");
        String[] ids = {policeId, ambulanceId, fireId};
        Client[] responders = {police, ambulance, fire};
        for (int i = 0; i < ids.length; i++) {
          operator.send("8\n" + id + "\n" + ids[i] + "\ny\n");
          responders[i].waitFor("ASSIGNMENT_OFFERED");
        }
        for (Client responder : responders) {
          responder.send("5\n");
          responder.waitFor("My offers/current assignment");
        }
        citizen.send("5\n" + id + "\n");
        citizen.waitFor("POLICE: OFFERED");
        citizen.waitFor("AMBULANCE: OFFERED");
        citizen.waitFor("FIRE_RESCUE: OFFERED");
        String assignmentId =
            lastMatch(
                "\"assignment\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"(" + UUID_PATTERN + ")\"",
                log("fire1.log"));
        operator.send("10\n" + id + "\n" + assignmentId + "\nINTERNAL-SMOKE-REASON\ny\n");
        fire.waitFor("\"state\" : \"CANCELLED\"");
        operator.send("8\n" + id + "\n" + replacementId + "\ny\n");
        replacement.waitFor("ASSIGNMENT_OFFERED");
        citizen.send("3\nOTHER\nFictional duplicate\nReject this academic example\n\n\n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        String rejectedId = id;
        while (rejectedId.equals(id) && System.nanoTime() < deadline) {
          Thread.sleep(40);
          rejectedId =
              lastMatch(
                  "Confirmed incident reference: (" + UUID_PATTERN + ")", log("citizen1.log"));
        }
        if (rejectedId.equals(id)) throw new IllegalStateException("Second report not confirmed");
        operator.send("11\n" + rejectedId + "\nPublic duplicate explanation\ny\n");
        citizen.waitFor("\"state\" : \"REJECTED\"");
        operator.send("4\n");
        operator.waitFor("TEST_NOTIFICATION");
        for (String name : List.of("citizen1", "police1", "ambulance1", "fire1", "fire2"))
          if (log(name + ".log").contains("INTERNAL-SMOKE-REASON"))
            throw new IllegalStateException("Private note leaked to " + name);
        if (log("police1.log").contains("\"serviceType\" : \"AMBULANCE\"")
            || log("ambulance1.log").contains("\"serviceType\" : \"POLICE\""))
          throw new IllegalStateException("Offer routed to unrelated team");
        System.out.println(
            "PASS: actual server + six client JVMs; report -> operator event -> priority -> three"
                + " service offers -> citizen service timeline -> cancel -> replacement offer ->"
                + " reject; private notes and offer recipients checked.");
      }
      for (String name : List.of("operator", "citizen1", "police1", "ambulance1", "fire1", "fire2"))
        if (!log(name + ".log").contains("Logged out"))
          throw new IllegalStateException(name + " did not log out cleanly");
      System.out.println("PASS: all console clients logged out and exited.");
      server.destroy();
      if (!server.waitFor(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Server did not stop");
      server = start("server", "server-restart.log");
      String restarted = await("server-restart.log", "TCP server listening on");
      int restartPort =
          Integer.parseInt(lastMatch("TCP server listening on 127\\.0\\.0\\.1:(\\d+)", restarted));
      Process recovered = start("client", "citizen-restart.log");
      String firstId =
          lastMatch(
              "Confirmed incident reference: (" + UUID_PATTERN + ")",
              log("citizen1.log")
                  .substring(
                      0,
                      log("citizen1.log").indexOf("Confirmed incident reference:")
                          + "Confirmed incident reference: ".length()
                          + 36));
      try (Writer writer =
          new OutputStreamWriter(recovered.getOutputStream(), StandardCharsets.UTF_8)) {
        writer.write(
            "127.0.0.1\n"
                + restartPort
                + "\n2\ncitizen1\nDemoPass123!\n5\n"
                + firstId
                + "\n9\n0\n");
      }
      finished(recovered);
      String recoveredLog = log("citizen-restart.log");
      if (!recoveredLog.contains("POLICE: OFFERED")
          || !recoveredLog.contains("AMBULANCE: OFFERED")
          || !recoveredLog.contains("FIRE_RESCUE: CANCELLED")
          || !recoveredLog.contains("Logged out"))
        throw new IllegalStateException(
            "Saved incident/service timeline was not recovered after process restart");
      System.out.println(
          "PASS: restarted server and a new citizen JVM recover the saved report, service"
              + " assignments and timeline. Evidence: "
              + evidence);
    } finally {
      server.destroy();
      if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly();
    }
  }

  static String teamId(String text, String name) {
    return lastMatch(
        "\"id\"\\s*:\\s*\"("
            + UUID_PATTERN
            + ")\"\\s*,\\s*\"name\"\\s*:\\s*\""
            + name
            + " simulated team\"",
        text);
  }
}
