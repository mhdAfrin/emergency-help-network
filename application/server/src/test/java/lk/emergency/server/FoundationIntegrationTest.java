package lk.emergency.server;

import static lk.emergency.common.Types.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FoundationIntegrationTest {
  @TempDir Path dir;
  Database db;
  TcpServer server;
  AuthService auth;

  @BeforeEach
  void start() throws Exception {
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    auth.seed();
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
  }

  @AfterEach
  void stop() {
    if (server != null) server.close();
    if (db != null) db.close();
  }

  class Peer implements AutoCloseable {
    Socket socket;
    List<Envelope> events = new ArrayList<>();

    Peer() throws Exception {
      socket = new Socket("127.0.0.1", server.port());
      socket.setSoTimeout(12000);
    }

    Envelope call(String type, Object data) throws Exception {
      Envelope request = Envelope.request(type, UUID.randomUUID().toString(), data);
      FrameCodec.write(socket.getOutputStream(), request);
      Envelope response;
      do {
        response = FrameCodec.read(socket.getInputStream());
        if (response != null && response.kind() == Kind.EVENT) events.add(response);
      } while (response != null && response.kind() == Kind.EVENT);
      assertNotNull(response);
      assertEquals(request.requestId(), response.requestId());
      return response;
    }

    Envelope login(String name) throws Exception {
      return call("LOGIN", new Login(name, "DemoPass123!"));
    }

    public void close() throws Exception {
      socket.close();
    }
  }

  void code(Envelope response, ErrorCode code) {
    assertFalse(response.ok());
    assertEquals(code, response.error().code());
  }

  @Test
  void registrationLoginDuplicateAndUnknownFields() throws Exception {
    try (Peer p = new Peer()) {
      assertTrue(
          p.call(
                  "REGISTER_CITIZEN",
                  new Registration("newCitizen", "සිසුවා", " password! ", "synthetic"))
              .ok());
      code(
          p.call(
              "REGISTER_CITIZEN", new Registration("newCitizen", "Duplicate", "password!", null)),
          ErrorCode.VALIDATION_ERROR);
      code(
          p.call(
              "REGISTER_CITIZEN",
              Map.of(
                  "username",
                  "hacker",
                  "displayName",
                  "H",
                  "password",
                  "password!",
                  "role",
                  "OPERATOR")),
          ErrorCode.VALIDATION_ERROR);
      code(p.call("LOGIN", new Login("newCitizen", "wrongpassword")), ErrorCode.UNAUTHENTICATED);
      assertTrue(p.call("LOGIN", new Login("newCitizen", " password! ")).ok());
      assertEquals("CITIZEN", p.call("WHO_AM_I", Map.of()).payload().get("role").asText());
      assertTrue(p.call("LOGOUT", Map.of()).ok());
      code(p.call("WHO_AM_I", Map.of()), ErrorCode.UNAUTHENTICATED);
    }
  }

  @Test
  void permissionsAndPrivateEvents() throws Exception {
    try (Peer citizen = new Peer();
        Peer operator = new Peer();
        Peer responder = new Peer()) {
      code(citizen.call("LIST_TEAMS", Map.of()), ErrorCode.UNAUTHENTICATED);
      assertTrue(citizen.login("citizen1").ok());
      code(citizen.call("LIST_TEAMS", Map.of()), ErrorCode.FORBIDDEN);
      code(citizen.call("ASSIGN_TEAM", Map.of()), ErrorCode.FORBIDDEN);
      code(citizen.call("TEST_EVENT", Map.of()), ErrorCode.FORBIDDEN);
      assertTrue(operator.login("operator").ok());
      assertEquals(4, operator.call("LIST_TEAMS", Map.of()).payload().size());
      assertTrue(operator.call("TEST_EVENT", Map.of()).ok());
      assertEquals("TEST_NOTIFICATION", operator.events.getFirst().type());
      assertTrue(responder.login("fire1").ok());
      Envelope own = responder.call("LIST_TEAMS", Map.of());
      assertEquals(1, own.payload().size());
      assertEquals("ONLINE", own.payload().get(0).get("presence").asText());
      assertTrue(citizen.call("PING", Map.of()).ok());
      assertTrue(citizen.events.isEmpty());
      code(operator.call("UPDATE_ASSIGNMENT_STATUS", Map.of()), ErrorCode.FORBIDDEN);
    }
  }

  @Test
  void newerLoginReplacesAndNotifiesOldSocket() throws Exception {
    try (Peer old = new Peer();
        Peer fresh = new Peer()) {
      assertTrue(old.login("citizen1").ok());
      assertTrue(fresh.login("citizen1").ok());
      Envelope event = FrameCodec.read(old.socket.getInputStream());
      assertNotNull(event);
      assertEquals("SESSION_REPLACED", event.type());
      assertNull(FrameCodec.read(old.socket.getInputStream()));
      assertTrue(fresh.call("WHO_AM_I", Map.of()).ok());
    }
  }

  @Test
  void concurrentSocketClientsAndMalformedIsolation() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(20);
    try {
      List<Future<Boolean>> jobs = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        jobs.add(
            workers.submit(
                () -> {
                  try (Peer peer = new Peer()) {
                    return peer.call("PING", Map.of())
                        .payload()
                        .get("message")
                        .asText()
                        .equals("PONG");
                  }
                }));
      for (Future<Boolean> job : jobs) assertTrue(job.get(15, TimeUnit.SECONDS));
    } finally {
      workers.shutdownNow();
    }
    try (Peer broken = new Peer()) {
      new DataOutputStream(broken.socket.getOutputStream()).writeInt(-1);
      assertNull(FrameCodec.read(broken.socket.getInputStream()));
    }
    try (Peer broken = new Peer()) {
      new DataOutputStream(broken.socket.getOutputStream()).writeInt(1);
      broken.socket.getOutputStream().write('{');
      assertNull(FrameCodec.read(broken.socket.getInputStream()));
    }
    try (Peer healthy = new Peer()) {
      assertTrue(healthy.call("PING", Map.of()).ok());
    }
  }

  @Test
  void connectionCapAndCleanShutdown() throws Exception {
    server.close();
    server = new TcpServer("127.0.0.1", 0, 1, db, auth);
    try (Peer first = new Peer()) {
      assertTrue(first.call("PING", Map.of()).ok());
      try (Peer excess = new Peer()) {
        assertNull(FrameCodec.read(excess.socket.getInputStream()));
      }
      server.close();
      assertNull(FrameCodec.read(first.socket.getInputStream()));
    }
  }

  @Test
  void accountsAndTeamsSurviveRestartAndSeedIsRepeatable() throws Exception {
    auth.register(new Registration("persisted", "Persistent Citizen", "persistent-pass", null));
    auth.seed();
    assertEquals(
        Integer.valueOf(8),
        db.call(
            c -> {
              try (var s = c.createStatement();
                  var r = s.executeQuery("SELECT COUNT(*) FROM users")) {
                r.next();
                return r.getInt(1);
              }
            }));
    server.close();
    db.close();
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
    try (Peer p = new Peer()) {
      assertTrue(p.call("LOGIN", new Login("persisted", "persistent-pass")).ok());
    }
    try (Peer p = new Peer()) {
      assertTrue(p.login("operator").ok());
      Envelope teams = p.call("LIST_TEAMS", Map.of());
      assertEquals(4, teams.payload().size());
      teams.payload().forEach(t -> assertEquals("OFFLINE", t.get("presence").asText()));
    }
  }

  @Test
  void unsupportedVersionAndEnvelopeValidation() throws Exception {
    try (Peer p = new Peer()) {
      FrameCodec.write(
          p.socket.getOutputStream(),
          new Envelope(
              2,
              Kind.REQUEST,
              "PING",
              UUID.randomUUID().toString(),
              null,
              Json.tree(Map.of()),
              null,
              null));
      code(FrameCodec.read(p.socket.getInputStream()), ErrorCode.UNSUPPORTED_VERSION);
    }
    try (Peer p = new Peer()) {
      code(p.call("NOT_A_COMMAND", Map.of()), ErrorCode.VALIDATION_ERROR);
    }
  }
}
