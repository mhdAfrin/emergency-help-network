import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Full actual-process console demonstration with a fixed ephemeral port for reconnect/restart. */
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

  static String log(String file) throws IOException {
    Path path = evidence.resolve(file);
    return Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "";
  }

  static String await(String file, String text, int offset) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
    while (System.nanoTime() < end) {
      String current = log(file);
      if (current.length() >= offset && current.substring(offset).contains(text)) return current;
      Thread.sleep(40);
    }
    throw new IllegalStateException(
        file + " missing " + text + " after offset " + offset + "; inspect " + evidence);
  }

  static String match(String expression, String text) {
    Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(text);
    String result = null;
    while (matcher.find()) result = matcher.group(1);
    if (result == null) throw new IllegalStateException("Pattern missing: " + expression);
    return result;
  }

  static void finish(Process process) throws Exception {
    if (!process.waitFor(25, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("Console process timed out");
    }
    if (process.exitValue() != 0)
      throw new IllegalStateException("Process failed: " + process.exitValue());
  }

  static class Client implements AutoCloseable {
    final String name, role, file;
    final Process process;
    final Writer input;

    Client(String name, String role, int port) throws Exception {
      this.name = name;
      this.role = role;
      file = name + ".log";
      process = start("client", file);
      input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
      try {
        send("127.0.0.1\n" + port + "\n2\n" + name + "\nDemoPass123!\n");
        await(file, "[" + role + "]", 0);
      } catch (Exception e) {
        process.destroyForcibly();
        input.close();
        throw e;
      }
    }

    void send(String value) throws IOException {
      input.write(value);
      input.flush();
    }

    String action(String input, String expected) throws Exception {
      int marker = log(file).length();
      send(input);
      String result = await(file, expected, marker);
      await(file, "[" + role + "]", marker);
      return result;
    }

    void relogin(int marker) throws Exception {
      await(file, "Connected to server. Login required.", marker);
      action("2\n" + name + "\nDemoPass123!\n", "Authorized snapshot synchronized.");
    }

    public void close() throws Exception {
      if (process.isAlive())
        try {
          send("9\n0\n");
          input.close();
          finish(process);
        } catch (Exception e) {
          process.destroyForcibly();
          throw e;
        }
    }
  }

  static String teamId(String text, String name) {
    return match(
        "\"id\"\\s*:\\s*\"("
            + UUID_PATTERN
            + ")\"\\s*,\\s*\"name\"\\s*:\\s*\""
            + name
            + " simulated team\"",
        text);
  }

  static void progress(Client client, String next) throws Exception {
    client.action("8\nPRIVATE-PROGRESS-NOTE\ny\n", "\"state\" : \"" + next + "\"");
  }

  static void freshCitizen(int port, String account, String file, String tail) throws Exception {
    Process client = start("client", file);
    try (Writer writer = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)) {
      writer.write("127.0.0.1\n" + port + "\n2\n" + account + "\nDemoPass123!\n" + tail);
    }
    finish(client);
  }

  public static void main(String[] args) throws Exception {
    root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
    work = root.resolve(".local/testing/final-smoke-" + UUID.randomUUID());
    Files.createDirectories(work.resolve("config"));
    evidence =
        root.getParent().resolve("docs/verification/evidence/member3-finalized/process-smoke");
    Files.createDirectories(evidence);
    javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    int port;
    try (ServerSocket reserved = new ServerSocket(0)) {
      port = reserved.getLocalPort();
    }
    Files.writeString(
        work.resolve("config/server.properties"),
        "server.host=127.0.0.1\nserver.port="
            + port
            + "\nserver.maxConnections=32\ndatabase.path=demo.db\n");
    finish(start("server", "seed.log", "--seed"));
    Process server = start("server", "server.log");
    String incidentId;
    try {
      await("server.log", "TCP server listening on", 0);
      try (Client op = new Client("operator", "OPERATOR", port);
          Client citizen = new Client("citizen1", "CITIZEN", port);
          Client police = new Client("police1", "RESPONDER", port);
          Client ambulance = new Client("ambulance1", "RESPONDER", port);
          Client fire = new Client("fire1", "RESPONDER", port);
          Client replacement = new Client("fire2", "RESPONDER", port)) {
        citizen.action("2\n", "PONG");
        String teams = op.action("3\n\n\n\n", "police1 simulated team");
        String reference =
            citizen.action(
                "3\nROAD_ACCIDENT\nFictional campus gate\nAcademic accident example\n\n\n",
                "Confirmed incident reference:");
        incidentId = match("Confirmed incident reference: (" + UUID_PATTERN + ")", reference);
        await(op.file, "INCIDENT_CREATED", 0);
        op.action("7\n" + incidentId + "\nHIGH\ny\n", "\"priority\" : \"HIGH\"");
        for (String team : List.of("police1", "ambulance1", "fire1"))
          op.action(
              "8\n" + incidentId + "\n" + teamId(teams, team) + "\ny\n", "\"state\" : \"OFFERED\"");
        for (Client responder : List.of(police, ambulance, fire)) {
          await(responder.file, "ASSIGNMENT_OFFERED", 0);
          responder.action("5\n", "\"assignment\"");
        }
        fire.action("7\nPRIVATE-DECLINE-REASON\ny\n", "\"state\" : \"DECLINED\"");
        op.action(
            "8\n" + incidentId + "\n" + teamId(teams, "fire2") + "\ny\n",
            "\"state\" : \"OFFERED\"");
        await(replacement.file, "ASSIGNMENT_OFFERED", 0);
        for (Client responder : List.of(police, ambulance, replacement))
          responder.action("6\n\ny\n", "\"state\" : \"ACCEPTED\"");
        progress(police, "EN_ROUTE");
        List<Client> all = List.of(op, citizen, police, ambulance, fire, replacement);
        Map<String, Integer> markers = new HashMap<>();
        for (Client client : all) markers.put(client.file, log(client.file).length());
        server.destroy();
        if (!server.waitFor(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Server shutdown timed out");
        server = start("server", "server-restart.log");
        await("server-restart.log", "TCP server listening on", 0);
        for (Client client : all) client.relogin(markers.get(client.file));
        if (!log(police.file).substring(markers.get(police.file)).contains("POLICE: EN_ROUTE"))
          throw new IllegalStateException(
              "Busy responder state was not restored by authorized snapshot");
        op.action("12\n" + incidentId + "\nPremature closure must fail\ny\n", "INVALID_TRANSITION");
        progress(police, "ARRIVED");
        String states = citizen.action("5\n" + incidentId + "\n\n", "POLICE: ARRIVED");
        if (!states.contains("AMBULANCE: ACCEPTED") || !states.contains("FIRE_RESCUE: ACCEPTED"))
          throw new IllegalStateException("Service progress was not displayed independently");
        progress(police, "COMPLETED");
        for (Client responder : List.of(ambulance, replacement)) {
          progress(responder, "EN_ROUTE");
          progress(responder, "ARRIVED");
          progress(responder, "COMPLETED");
        }
        op.action(
            "12\n" + incidentId + "\nPublic academic resolution\ny\n", "\"state\" : \"CLOSED\"");
        citizen.action("6\n", "\"state\" : \"CLOSED\"");
        fire.action("10\n", "PRIVATE-DECLINE-REASON");
        if (log(citizen.file).contains("PRIVATE-DECLINE-REASON")
            || log(citizen.file).contains("PRIVATE-PROGRESS-NOTE"))
          throw new IllegalStateException("Private notes leaked to citizen");
        if (log(police.file).contains("\"serviceType\" : \"AMBULANCE\""))
          throw new IllegalStateException("Unrelated responder assignment leaked");
        System.out.println(
            "PASS: six separate console client JVMs; report -> notify -> three service offers ->"
                + " decline -> replacement -> accept -> independent EN_ROUTE/ARRIVED/COMPLETED ->"
                + " eligible closure and histories.");
        System.out.println(
            "PASS: server process restart; clients reconnect while menu input is idle, require"
                + " login again, and restore BUSY service state through authorized snapshots.");
      }
      for (String name : List.of("operator", "citizen1", "police1", "ambulance1", "fire1", "fire2"))
        if (!log(name + ".log").contains("Logged out"))
          throw new IllegalStateException("Client did not log out: " + name);
      freshCitizen(
          port, "citizen1", "closed-history-restart.log", "5\n" + incidentId + "\n\n9\n0\n");
      if (!log("closed-history-restart.log").contains("\"state\" : \"CLOSED\"")
          || !log("closed-history-restart.log").contains("POLICE: COMPLETED"))
        throw new IllegalStateException("Closed history was not preserved for a new citizen JVM");
      // EOF after address intentionally leaves an unsent partial draft, not a dispatched report.
      freshCitizen(port, "citizen2", "draft-incomplete.log", "3\nOTHER\nSaved draft campus\n");
      freshCitizen(
          port,
          "citizen2",
          "draft-recovered.log",
          "3\ny\n\n\nRecovered draft description\n\n\n9\n0\n");
      String draft = log("draft-recovered.log");
      if (!draft.contains("Resume your saved unsent report?")
          || !draft.contains("Saved draft campus")
          || !draft.contains("Confirmed incident reference:"))
        throw new IllegalStateException("Unsent draft did not survive a client process restart");
      System.out.println(
          "PASS: a fresh citizen JVM recovers closed service history; an unsent report draft"
              + " survives client EOF/restart; privacy checks and all console exits passed."
              + " Evidence: "
              + evidence);
    } finally {
      server.destroy();
      if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly();
    }
  }
}
