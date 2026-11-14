package lk.emergency.server;

import static lk.emergency.common.Types.*;
import static lk.emergency.common.Types.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FinalWorkflowTest {
  @TempDir Path dir;
  Database db;
  AuthService auth;
  TcpServer server;
  List<Peer> peers = new ArrayList<>();

  @BeforeEach
  void start() throws Exception {
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    auth.seed();
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
  }

  @AfterEach
  void stop() throws Exception {
    for (var p : peers) p.close();
    server.close();
    db.close();
  }

  class Peer implements AutoCloseable {
    final Socket socket;
    final List<Envelope> events = new ArrayList<>();

    Peer(String user) throws Exception {
      socket = new Socket("127.0.0.1", server.port());
      socket.setSoTimeout(10000);
      if (user != null) ok(call("LOGIN", new Login(user, "DemoPass123!")));
    }

    Envelope call(String type, Object data) throws Exception {
      return send(Envelope.request(type, UUID.randomUUID().toString(), data));
    }

    Envelope send(Envelope request) throws Exception {
      FrameCodec.write(socket.getOutputStream(), request);
      while (true) {
        var frame = FrameCodec.read(socket.getInputStream());
        assertNotNull(frame);
        if (frame.kind() == Kind.EVENT) {
          events.add(frame);
          continue;
        }
        assertEquals(request.requestId(), frame.requestId());
        return frame;
      }
    }

    public void close() throws IOException {
      socket.close();
    }
  }

  Peer peer(String name) throws Exception {
    var p = new Peer(name);
    peers.add(p);
    return p;
  }

  JsonNode ok(Envelope e) {
    assertTrue(Boolean.TRUE.equals(e.ok()), e.toString());
    return e.payload();
  }

  void error(Envelope e, ErrorCode code) {
    assertFalse(Boolean.TRUE.equals(e.ok()));
    assertEquals(code, e.error().code());
  }

  JsonNode report(Peer p) throws Exception {
    return ok(
        p.call(
            "CREATE_INCIDENT",
            new IncidentInput(
                Category.ROAD_ACCIDENT, "Fictional campus", "Academic example", null, null)));
  }

  JsonNode details(Peer op, String id) throws Exception {
    return ok(op.call("GET_INCIDENT", new IncidentQuery(id, null, 0, 0)));
  }

  JsonNode team(Peer op, String name) throws Exception {
    for (var t : ok(op.call("LIST_TEAMS", Map.of())))
      if (t.get("name").asText().startsWith(name + " ")) return t;
    throw new AssertionError(name);
  }

  JsonNode assignment(Peer responder) throws Exception {
    var offers = ok(responder.call("LIST_ASSIGNMENTS", Map.of()));
    assertEquals(1, offers.size());
    return offers.get(0).get("assignment");
  }

  JsonNode offer(Peer op, String incidentId, String name) throws Exception {
    var i = details(op, incidentId).get("incident");
    var t = team(op, name);
    return ok(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(
                incidentId,
                t.get("id").asText(),
                i.get("revision").asLong(),
                t.get("revision").asLong())));
  }

  AssignmentResponseInput responseInput(
      Peer op, Peer responder, String name, AssignmentDecision decision, String reason)
      throws Exception {
    var a = assignment(responder);
    return new AssignmentResponseInput(
        a.get("id").asText(),
        decision,
        reason,
        a.get("revision").asLong(),
        details(op, a.get("incidentId").asText()).get("incident").get("revision").asLong(),
        team(op, name).get("revision").asLong());
  }

  void accept(Peer op, Peer responder, String name) throws Exception {
    ok(
        responder.call(
            "RESPOND_ASSIGNMENT",
            responseInput(
                op, responder, name, AssignmentDecision.ACCEPT, "Private acceptance note")));
  }

  JsonNode progress(Peer op, Peer responder, String name, AssignmentState next) throws Exception {
    var a = assignment(responder);
    return ok(
        responder.call(
            "UPDATE_ASSIGNMENT_STATUS",
            new AssignmentStatusInput(
                a.get("id").asText(),
                next,
                "PRIVATE-PROGRESS-NOTE",
                a.get("revision").asLong(),
                details(op, a.get("incidentId").asText()).get("incident").get("revision").asLong(),
                team(op, name).get("revision").asLong())));
  }

  Envelope close(Peer op, String id) throws Exception {
    return op.call(
        "CLOSE_INCIDENT",
        new IncidentClosureInput(
            id,
            "Public academic resolution",
            details(op, id).get("incident").get("revision").asLong()));
  }

  void complete(Peer op, Peer responder, String name) throws Exception {
    accept(op, responder, name);
    progress(op, responder, name, AssignmentState.EN_ROUTE);
    progress(op, responder, name, AssignmentState.ARRIVED);
    progress(op, responder, name, AssignmentState.COMPLETED);
  }

  @Test
  void threeServicesProgressIndependentlyAndClosureRequiresAllTerminal() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    var ambulance = peer("ambulance1");
    var fire = peer("fire1");
    String id = report(citizen).get("id").asText();
    offer(op, id, "police1");
    offer(op, id, "ambulance1");
    offer(op, id, "fire1");
    accept(op, police, "police1");
    progress(op, police, "police1", AssignmentState.EN_ROUTE);
    progress(op, police, "police1", AssignmentState.ARRIVED);
    var serviceStates = details(citizen, id).get("assignments");
    assertEquals(
        1, serviceStates.findValuesAsText("state").stream().filter("ARRIVED"::equals).count());
    assertEquals(
        2, serviceStates.findValuesAsText("state").stream().filter("OFFERED"::equals).count());
    error(close(op, id), INVALID_TRANSITION);
    progress(op, police, "police1", AssignmentState.COMPLETED);
    assertEquals("AVAILABLE", team(op, "police1").get("availability").asText());
    error(close(op, id), INVALID_TRANSITION);
    complete(op, ambulance, "ambulance1");
    complete(op, fire, "fire1");
    assertEquals("CLOSED", ok(close(op, id)).get("state").asText());
    assertEquals(
        1, ok(citizen.call("LIST_INCIDENTS", new IncidentFilter(null, null, 0, 5, true))).size());
    assertEquals(1, ok(police.call("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5, true))).size());
    assertTrue(ok(police.call("LIST_ASSIGNMENTS", Map.of())).isEmpty());
    error(
        op.call(
            "SET_PRIORITY",
            new PriorityUpdateInput(
                id, Priority.HIGH, details(op, id).get("incident").get("revision").asLong())),
        INVALID_TRANSITION);
  }

  @Test
  void declineIsPrivateReleasesReservationAndPermitsReplacement() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var fire = peer("fire1");
    var replacement = peer("fire2");
    String id = report(citizen).get("id").asText();
    offer(op, id, "fire1");
    String secret = "PRIVATE-DECLINE-REASON";
    var input = responseInput(op, fire, "fire1", AssignmentDecision.DECLINE, secret);
    Envelope request = Envelope.request("RESPOND_ASSIGNMENT", UUID.randomUUID().toString(), input);
    Envelope first = fire.send(request);
    assertEquals("DECLINED", ok(first).get("state").asText());
    assertEquals(first, fire.send(request));
    assertEquals("AVAILABLE", team(op, "fire1").get("availability").asText());
    assertEquals("ACTIVE", details(citizen, id).get("incident").get("state").asText());
    error(close(op, id), INVALID_TRANSITION);
    assertFalse(details(citizen, id).toString().contains(secret));
    assertTrue(details(op, id).get("operationalNotes").toString().contains(secret));
    assertTrue(
        ok(fire.call("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5, true)))
            .toString()
            .contains(secret));
    assertTrue(
        ok(replacement.call("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5, true))).isEmpty());
    for (var event : citizen.events) assertFalse(event.toString().contains(secret));
    offer(op, id, "fire2");
    complete(op, replacement, "fire2");
    assertEquals("CLOSED", ok(close(op, id)).get("state").asText());
  }

  @Test
  void skippedBackwardRepeatedAndUnauthorizedProgressAreRejected() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    var fire = peer("fire1");
    String id = report(citizen).get("id").asText();
    var offered = offer(op, id, "police1");
    var bad =
        new AssignmentStatusInput(
            offered.get("id").asText(), AssignmentState.ARRIVED, "", 0L, 1L, 1L);
    error(police.call("UPDATE_ASSIGNMENT_STATUS", bad), INVALID_TRANSITION);
    error(fire.call("UPDATE_ASSIGNMENT_STATUS", bad), NOT_FOUND);
    error(op.call("UPDATE_ASSIGNMENT_STATUS", bad), FORBIDDEN);
    var accepted = responseInput(op, police, "police1", AssignmentDecision.ACCEPT, "");
    ok(police.call("RESPOND_ASSIGNMENT", accepted));
    error(police.call("RESPOND_ASSIGNMENT", accepted), VERSION_CONFLICT);
    var current = assignment(police);
    error(
        police.call(
            "UPDATE_ASSIGNMENT_STATUS",
            new AssignmentStatusInput(
                current.get("id").asText(), AssignmentState.COMPLETED, "", 1L, 2L, 1L)),
        INVALID_TRANSITION);
    progress(op, police, "police1", AssignmentState.EN_ROUTE);
    current = assignment(police);
    error(
        police.call(
            "UPDATE_ASSIGNMENT_STATUS",
            new AssignmentStatusInput(
                current.get("id").asText(), AssignmentState.ACCEPTED, "", 2L, 3L, 1L)),
        INVALID_TRANSITION);
  }

  @Test
  void responderMutationChecksEveryRevisionAndRequiredDeclineReason() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    String id = report(citizen).get("id").asText();
    var a = offer(op, id, "police1");
    String aid = a.get("id").asText();
    error(
        police.call(
            "RESPOND_ASSIGNMENT",
            new AssignmentResponseInput(aid, AssignmentDecision.DECLINE, " ", 0L, 1L, 1L)),
        VALIDATION_ERROR);
    error(
        police.call(
            "RESPOND_ASSIGNMENT",
            new AssignmentResponseInput(aid, AssignmentDecision.ACCEPT, "", 1L, 1L, 1L)),
        VERSION_CONFLICT);
    error(
        police.call(
            "RESPOND_ASSIGNMENT",
            new AssignmentResponseInput(aid, AssignmentDecision.ACCEPT, "", 0L, 0L, 1L)),
        VERSION_CONFLICT);
    error(
        police.call(
            "RESPOND_ASSIGNMENT",
            new AssignmentResponseInput(aid, AssignmentDecision.ACCEPT, "", 0L, 1L, 0L)),
        VERSION_CONFLICT);
    error(
        police.call("RESPOND_ASSIGNMENT", Map.of("assignmentId", aid, "decision", "ACCEPT")),
        VALIDATION_ERROR);
    error(
        police.call(
            "RESPOND_ASSIGNMENT",
            Map.of(
                "assignmentId",
                aid,
                "decision",
                "ACCEPT",
                "actorId",
                "another",
                "expectedRevision",
                0,
                "expectedIncidentRevision",
                1,
                "expectedTeamRevision",
                1)),
        VALIDATION_ERROR);
  }

  @Test
  void closureAndProgressReplaysDoNotDuplicateTimeline() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    String id = report(citizen).get("id").asText();
    offer(op, id, "police1");
    accept(op, police, "police1");
    progress(op, police, "police1", AssignmentState.EN_ROUTE);
    progress(op, police, "police1", AssignmentState.ARRIVED);
    var a = assignment(police);
    Envelope done =
        Envelope.request(
            "UPDATE_ASSIGNMENT_STATUS",
            UUID.randomUUID().toString(),
            new AssignmentStatusInput(
                a.get("id").asText(),
                AssignmentState.COMPLETED,
                "",
                a.get("revision").asLong(),
                details(op, id).get("incident").get("revision").asLong(),
                team(op, "police1").get("revision").asLong()));
    Envelope first = police.send(done);
    ok(first);
    assertEquals(first, police.send(done));
    long rev = details(op, id).get("incident").get("revision").asLong();
    Envelope close =
        Envelope.request(
            "CLOSE_INCIDENT",
            UUID.randomUUID().toString(),
            new IncidentClosureInput(id, "Public resolution", rev));
    Envelope closed = op.send(close);
    ok(closed);
    assertEquals(closed, op.send(close));
    assertEquals(7, details(citizen, id).get("timeline").size());
    assertEquals(2, team(op, "police1").get("revision").asLong());
  }

  @Test
  void closureWithNoCompletedAssistanceOrUnfinishedOfferIsRefused() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    String id = report(citizen).get("id").asText();
    error(close(op, id), INVALID_TRANSITION);
    offer(op, id, "police1");
    ok(
        police.call(
            "RESPOND_ASSIGNMENT",
            responseInput(op, police, "police1", AssignmentDecision.DECLINE, "Cannot attend")));
    error(close(op, id), INVALID_TRANSITION);
  }

  @Test
  void frozenSnapshotHasOneWatermarkAndBuffersSubsequentChanges() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    for (int i = 0; i < 8; i++) report(citizen);
    var page = ok(op.call("GET_SNAPSHOT", Map.of()));
    String token = page.get("snapshotId").asText(), epoch = page.get("epoch").asText();
    long watermark = page.get("sequence").asLong();
    List<JsonNode> frozen = new ArrayList<>();
    page.get("incidents").forEach(frozen::add);
    String newId = report(citizen).get("id").asText();
    String modified = frozen.getFirst().get("id").asText();
    ok(op.call("SET_PRIORITY", new PriorityUpdateInput(modified, Priority.HIGH, 0L)));
    while (!page.get("nextOffset").isNull()) {
      page = ok(op.call("GET_SNAPSHOT", new SnapshotQuery(token, page.get("nextOffset").asInt())));
      assertEquals(watermark, page.get("sequence").asLong());
      assertEquals(epoch, page.get("epoch").asText());
      page.get("incidents").forEach(frozen::add);
    }
    assertEquals(8, frozen.size());
    assertTrue(frozen.stream().noneMatch(x -> x.get("id").asText().equals(newId)));
    assertTrue(frozen.stream().allMatch(x -> x.get("revision").asLong() == 0));
    ok(op.call("PING", Map.of()));
    assertTrue(
        op.events.stream()
            .anyMatch(
                e ->
                    e.sequence() != null
                        && e.sequence() > watermark
                        && e.payload().toString().contains(newId)));
    error(citizen.call("GET_SNAPSHOT", new SnapshotQuery(token, 0)), INVALID_TRANSITION);
  }

  @Test
  void snapshotsContainOnlyAuthorizedActiveState() throws Exception {
    var op = peer("operator");
    var a = peer("citizen1");
    var b = peer("citizen2");
    var police = peer("police1");
    var fire = peer("fire1");
    String owned = report(a).get("id").asText(), other = report(b).get("id").asText();
    offer(op, owned, "police1");
    var citizen = ok(a.call("GET_SNAPSHOT", Map.of()));
    assertEquals(1, citizen.get("incidents").size());
    assertEquals(0, citizen.get("teams").size());
    assertFalse(citizen.toString().contains(other));
    var responder = ok(police.call("GET_SNAPSHOT", Map.of()));
    assertEquals(1, responder.get("incidents").size());
    assertEquals(1, responder.get("assignments").size());
    assertEquals(1, responder.get("teams").size());
    assertFalse(responder.toString().contains(other));
    assertTrue(ok(fire.call("GET_SNAPSHOT", Map.of())).get("assignments").isEmpty());
  }

  @Test
  void presenceEventsAreOrderedAndDisconnectNeverReleasesBusy() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    String id = report(citizen).get("id").asText();
    offer(op, id, "police1");
    police.close();
    for (int i = 0; i < 100 && team(op, "police1").get("presence").asText().equals("ONLINE"); i++)
      Thread.sleep(10);
    assertEquals("OFFLINE", team(op, "police1").get("presence").asText());
    assertEquals("BUSY", team(op, "police1").get("availability").asText());
    ok(op.call("PING", Map.of()));
    assertTrue(
        op.events.stream()
            .anyMatch(
                e ->
                    e.type().equals("TEAM_UPDATED")
                        && e.payload().path("presence").asText().equals("OFFLINE")));
    long previous = -1;
    for (var e : op.events)
      if (e.sequence() != null) {
        assertTrue(e.sequence() > previous);
        previous = e.sequence();
      }
  }

  @Test
  void startupReconcilesBusyFromSavedActiveAssignments() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    String id = report(citizen).get("id").asText();
    var assignment = offer(op, id, "police1");
    for (var p : peers) p.close();
    peers.clear();
    server.close();
    db.transaction(
        c -> {
          try (var s = c.createStatement()) {
            s.executeUpdate("UPDATE teams SET availability='AVAILABLE'");
          }
          return null;
        });
    db.close();
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
    op = peer("operator");
    police = peer("police1");
    assertEquals("BUSY", team(op, "police1").get("availability").asText());
    var snapshot = ok(police.call("GET_SNAPSHOT", Map.of()));
    assertEquals(assignment.get("id"), snapshot.get("assignments").get(0).get("id"));
  }

  @Test
  void idleAndPartialFramePeersExpireButHealthyHeartbeatPeerSurvives() throws Exception {
    server.close();
    server = new TcpServer("127.0.0.1", 0, 32, db, auth, new NetworkTiming(100, 800, 500));
    try (Socket idle = new Socket("127.0.0.1", server.port());
        Socket partial = new Socket("127.0.0.1", server.port())) {
      idle.setSoTimeout(3000);
      partial.setSoTimeout(3000);
      partial.getOutputStream().write(new byte[] {0, 0});
      Peer healthy = peer(null);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<?> pulses =
            pool.submit(
                () -> {
                  try {
                    for (int i = 0; i < 14; i++) {
                      ok(healthy.call("PING", Map.of()));
                      Thread.sleep(100);
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        assertEquals(-1, idle.getInputStream().read());
        assertEquals(-1, partial.getInputStream().read());
        pulses.get(5, TimeUnit.SECONDS);
        ok(healthy.call("PING", Map.of()));
      } finally {
        pool.shutdownNow();
      }
    }
  }

  @Test
  void realSlowReaderIsIsolatedFromHealthyReporter() throws Exception {
    try (Socket slow = new Socket()) {
      slow.setReceiveBufferSize(1024);
      slow.connect(new InetSocketAddress("127.0.0.1", server.port()));
      FrameCodec.write(
          slow.getOutputStream(),
          Envelope.request(
              "LOGIN", UUID.randomUUID().toString(), new Login("operator", "DemoPass123!")));
      var citizen = peer("citizen1");
      Thread.sleep(100);
      for (int i = 0; i < 150; i++)
        ok(
            citizen.call(
                "CREATE_INCIDENT",
                new IncidentInput(Category.OTHER, "Campus", "\u0001".repeat(1000), null, null)));
      for (int i = 0; i < 120 && server.sessionCount() > 1; i++) Thread.sleep(50);
      assertEquals(
          1, server.sessionCount(), "Slow reader should overflow its queue or hit writer deadline");
      ok(citizen.call("PING", Map.of()));
    }
  }

  @Test
  void ownAssignmentTimelineRetainsOwnNotesButHidesOperatorPrivateCancellation() throws Exception {
    var op = peer("operator");
    var citizen = peer("citizen1");
    var police = peer("police1");
    var fire = peer("fire1");
    String id = report(citizen).get("id").asText();
    offer(op, id, "police1");
    var accepted =
        responseInput(op, police, "police1", AssignmentDecision.ACCEPT, "PRIVATE-OWN-ACCEPT-NOTE");
    ok(police.call("RESPOND_ASSIGNMENT", accepted));
    var a = assignment(police);
    ok(
        op.call(
            "CANCEL_ASSIGNMENT",
            new AssignmentCancellationInput(
                a.get("id").asText(), "PRIVATE-OP-CANCEL-NOTE", 1L, 2L, 1L)));
    var timeline = ok(police.call("GET_ASSIGNMENT", new AssignmentQuery(a.get("id").asText(), 0)));
    assertTrue(timeline.toString().contains("PRIVATE-OWN-ACCEPT-NOTE"));
    assertFalse(timeline.toString().contains("PRIVATE-OP-CANCEL-NOTE"));
    assertFalse(
        ok(police.call("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5, true)))
            .toString()
            .contains("PRIVATE-OP-CANCEL-NOTE"));
    assertFalse(details(citizen, id).toString().contains("PRIVATE-"));
    assertTrue(
        details(op, id).get("operationalNotes").toString().contains("PRIVATE-OP-CANCEL-NOTE"));
    error(fire.call("GET_ASSIGNMENT", new AssignmentQuery(a.get("id").asText(), 0)), NOT_FOUND);
    error(citizen.call("GET_ASSIGNMENT", new AssignmentQuery(a.get("id").asText(), 0)), FORBIDDEN);
  }
}
