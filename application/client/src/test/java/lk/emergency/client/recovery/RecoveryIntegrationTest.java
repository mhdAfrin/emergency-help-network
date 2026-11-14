package lk.emergency.client.recovery;

import static lk.emergency.common.Types.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import lk.emergency.client.network.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RecoveryIntegrationTest {
  @TempDir Path dir;
  Database db;
  TcpServer server;
  AuthService auth;

  @BeforeEach
  void start() throws Exception {
    db = new Database(dir.resolve("server.db"));
    auth = new AuthService(db);
    auth.seed();
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
  }

  @AfterEach
  void stop() {
    server.close();
    db.close();
  }

  JsonNode ok(Envelope e) {
    assertTrue(Boolean.TRUE.equals(e.ok()), e.toString());
    return e.payload();
  }

  void until(BooleanSupplier condition, long millis) throws Exception {
    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(20);
    assertTrue(condition.getAsBoolean());
  }

  JsonNode login(Transport p, String user) throws Exception {
    return ok(p.call("LOGIN", new Login(user, "DemoPass123!")));
  }

  JsonNode incident(Transport p) throws Exception {
    return ok(
        p.call(
            "CREATE_INCIDENT",
            new IncidentInput(Category.FIRE, "Academic campus", "Fiction only", null, null)));
  }

  JsonNode team(Transport p) throws Exception {
    for (var t : ok(p.call("LIST_TEAMS", Map.of())))
      if (t.get("name").asText().startsWith("police1 ")) return t;
    throw new AssertionError();
  }

  @Test
  void reconnectRequiresLoginAndSnapshotRestoresBusyAssignmentAfterServerRestart()
      throws Exception {
    LiveState state = new LiveState();
    Queue<String> status = new ConcurrentLinkedQueue<>();
    int port = server.port();
    try (ConnectionManager operator = new ConnectionManager("127.0.0.1", port, e -> {});
        ConnectionManager citizen = new ConnectionManager("127.0.0.1", port, e -> {});
        ReconnectingClient police =
            new ReconnectingClient("127.0.0.1", port, state::accept, status::add)) {
      login(operator, "operator");
      login(citizen, "citizen1");
      state.begin();
      UserView user = Validation.payload(login(police, "police1"), UserView.class);
      state.synchronize(police, user);
      var incident = incident(citizen);
      var team = team(operator);
      JsonNode assignment =
          ok(
              operator.call(
                  "ASSIGN_TEAM",
                  new AssignmentInput(
                      incident.get("id").asText(), team.get("id").asText(), 0L, 0L)));
      String id = assignment.get("id").asText();
      until(() -> state.assignment(id) != null, 3000);
      long generation = police.generation();
      server.close();
      until(() -> !police.authenticated(), 3000);
      server = new TcpServer("127.0.0.1", port, 32, db, auth);
      until(() -> police.connected() && police.generation() > generation, 9000);
      assertFalse(police.authenticated());
      assertThrows(IOException.class, () -> police.call("LIST_ASSIGNMENTS", Map.of()));
      state.begin();
      user = Validation.payload(login(police, "police1"), UserView.class);
      state.synchronize(police, user);
      assertEquals("OFFERED", state.assignment(id).get("state").asText());
      assertEquals("BUSY", state.team(team.get("id").asText()).get("availability").asText());
    }
  }

  @Test
  void durableSameIdRetryReconcilesCommittedUnreadResponseAfterNewClient() throws Exception {
    String userId = auth.login(new Login("citizen1", "DemoPass123!")).id();
    LocalJournal journal = new LocalJournal(dir.resolve("journal"), "localhost:" + server.port());
    Envelope request =
        Envelope.request(
            "CREATE_INCIDENT",
            UUID.randomUUID().toString(),
            new IncidentInput(Category.OTHER, "Campus", "Committed but unread", null, null));
    journal.put(userId, request);
    try (var socket = new java.net.Socket("127.0.0.1", server.port())) {
      FrameCodec.write(
          socket.getOutputStream(),
          Envelope.request(
              "LOGIN", UUID.randomUUID().toString(), new Login("citizen1", "DemoPass123!")));
      assertTrue(FrameCodec.read(socket.getInputStream()).ok());
      FrameCodec.write(socket.getOutputStream(), request);
      until(
          () ->
              db.call(
                  c -> {
                    try (var p =
                        c.prepareStatement(
                            "SELECT 1 FROM processed_requests WHERE user_id=? AND request_id=?")) {
                      p.setString(1, userId);
                      p.setString(2, request.requestId());
                      try (var rows = p.executeQuery()) {
                        return rows.next();
                      }
                    }
                  }),
          4000);
    }
    try (ReconnectingClient recovered =
        new ReconnectingClient("127.0.0.1", server.port(), e -> {}, s -> {})) {
      login(recovered, "citizen1");
      PendingActions pending =
          new PendingActions(
              recovered, new LocalJournal(dir.resolve("journal"), "localhost:" + server.port()));
      assertEquals(request.requestId(), pending.list(userId).getFirst().request().requestId());
      var response = pending.resolve(userId, request.requestId(), true, Duration.ofSeconds(3));
      ok(response);
      assertTrue(pending.list(userId).isEmpty());
      assertEquals(
          Long.valueOf(1),
          db.call(
              c -> {
                try (var s = c.createStatement();
                    var rows = s.executeQuery("SELECT COUNT(*) FROM incidents")) {
                  rows.next();
                  return rows.getLong(1);
                }
              }));
    }
  }

  @Test
  void subscribeBeforeSnapshotAndBufferedUpdatesKeepLatestRevision() throws Exception {
    LiveState state = new LiveState();
    try (ConnectionManager operator =
            new ConnectionManager("127.0.0.1", server.port(), state::accept);
        ConnectionManager citizen = new ConnectionManager("127.0.0.1", server.port(), e -> {})) {
      state.begin();
      UserView user = Validation.payload(login(operator, "operator"), UserView.class);
      login(citizen, "citizen1");
      for (int i = 0; i < 7; i++) incident(citizen);
      state.synchronize(operator, user);
      assertEquals(7, state.incidentCount());
      JsonNode created = incident(citizen);
      String id = created.get("id").asText();
      until(() -> state.incident(id) != null, 3000);
      ok(operator.call("SET_PRIORITY", new PriorityUpdateInput(id, Priority.HIGH, 0L)));
      until(() -> state.incident(id).get("revision").asLong() == 1, 3000);
      assertEquals("HIGH", state.incident(id).get("priority").asText());
    }
  }

  @Test
  void reconnectPolicyUsesRequiredBackoffWithCappedJitter() {
    assertEquals(1000, ReconnectPolicy.delayMillis(0, 0.5));
    assertEquals(2000, ReconnectPolicy.delayMillis(1, 0.5));
    assertEquals(4000, ReconnectPolicy.delayMillis(2, 0.5));
    assertEquals(8000, ReconnectPolicy.delayMillis(3, 0.5));
    assertEquals(15000, ReconnectPolicy.delayMillis(4, 0.5));
    assertTrue(ReconnectPolicy.delayMillis(99, 1) <= 15000);
    assertTrue(ReconnectPolicy.delayMillis(0, 0) >= 800);
  }
}
