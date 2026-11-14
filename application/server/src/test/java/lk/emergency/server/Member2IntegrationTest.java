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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class Member2IntegrationTest {
  @TempDir Path dir;
  Database db;
  AuthService auth;
  TcpServer server;
  final List<Peer> peers = new ArrayList<>();

  @BeforeEach
  void start() throws Exception {
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    auth.seed();
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
  }

  @AfterEach
  void stop() throws Exception {
    for (Peer p : peers) p.close();
    server.close();
    db.close();
  }

  class Peer implements AutoCloseable {
    Socket socket;
    List<Envelope> events = new ArrayList<>();

    Peer(String username) throws Exception {
      socket = new Socket("127.0.0.1", server.port());
      socket.setSoTimeout(6000);
      ok(call("LOGIN", new Login(username, "DemoPass123!")));
    }

    Envelope call(String type, Object payload) throws Exception {
      return send(Envelope.request(type, UUID.randomUUID().toString(), payload));
    }

    Envelope send(Envelope request) throws Exception {
      FrameCodec.write(socket.getOutputStream(), request);
      return response(request.requestId());
    }

    Envelope response(String id) throws Exception {
      while (true) {
        Envelope e = FrameCodec.read(socket.getInputStream());
        assertNotNull(e);
        if (e.kind() == Kind.EVENT) {
          events.add(e);
          continue;
        }
        assertEquals(id, e.requestId());
        return e;
      }
    }

    Envelope event(String type) throws Exception {
      for (var iterator = events.iterator(); iterator.hasNext(); ) {
        Envelope e = iterator.next();
        if (e.type().equals(type)) {
          iterator.remove();
          return e;
        }
      }
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      socket.setSoTimeout(3000);
      try {
        while (System.nanoTime() < end) {
          Envelope e = FrameCodec.read(socket.getInputStream());
          assertNotNull(e);
          assertEquals(Kind.EVENT, e.kind());
          if (e.type().equals(type)) return e;
          events.add(e);
        }
        throw new AssertionError("Missing event " + type);
      } finally {
        socket.setSoTimeout(6000);
      }
    }

    void quiet() throws Exception {
      assertTrue(events.isEmpty(), events.toString());
      socket.setSoTimeout(180);
      try {
        assertThrows(SocketTimeoutException.class, () -> FrameCodec.read(socket.getInputStream()));
      } finally {
        socket.setSoTimeout(6000);
      }
    }

    public void close() throws IOException {
      socket.close();
    }
  }

  Peer peer(String user) throws Exception {
    Peer p = new Peer(user);
    peers.add(p);
    return p;
  }

  JsonNode ok(Envelope response) {
    assertTrue(Boolean.TRUE.equals(response.ok()), response.toString());
    return response.payload();
  }

  void error(Envelope response, ErrorCode code) {
    assertFalse(Boolean.TRUE.equals(response.ok()));
    assertEquals(code, response.error().code());
  }

  IncidentInput input() {
    return new IncidentInput(
        Category.ROAD_ACCIDENT, "සිංහල campus 🚑", "Academic fictional accident", 6.1, 80.1);
  }

  JsonNode report(Peer citizen) throws Exception {
    return ok(citizen.call("CREATE_INCIDENT", input()));
  }

  JsonNode detail(Peer p, String id) throws Exception {
    return ok(p.call("GET_INCIDENT", new IncidentQuery(id, null, 0, 0)));
  }

  JsonNode team(Peer op, String name) throws Exception {
    for (JsonNode t : ok(op.call("LIST_TEAMS", Map.of())))
      if (t.get("name").asText().startsWith(name + " ")) return t;
    throw new AssertionError(name);
  }

  long count(String table) {
    return db.call(
        c -> {
          try (var s = c.createStatement();
              var rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
          }
        });
  }

  JsonNode offer(Peer op, JsonNode incident, JsonNode team) throws Exception {
    return ok(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(
                incident.get("id").asText(),
                team.get("id").asText(),
                incident.get("revision").asLong(),
                team.get("revision").asLong())));
  }

  AssignmentCancellationInput cancelInput(
      JsonNode assignment, JsonNode incident, JsonNode team, String reason) {
    return new AssignmentCancellationInput(
        assignment.get("id").asText(),
        reason,
        assignment.get("revision").asLong(),
        incident.get("revision").asLong(),
        team.get("revision").asLong());
  }

  void fixtureState(String id, String state) {
    db.transaction(
        c -> {
          try (var p = c.prepareStatement("UPDATE assignments SET state=? WHERE id=?")) {
            p.setString(1, state);
            p.setString(2, id);
            p.executeUpdate();
          }
          return null;
        });
  }

  void operator2() {
    var hash = new PasswordHasher().hash("DemoPass123!");
    db.transaction(
        c -> {
          new UserRepository().insert(c, "operator2", "Second operator", null, Role.OPERATOR, hash);
          return null;
        });
  }

  @Test
  void committedReportNotifiesOnlyOperatorsAndOwnershipIsEnforced() throws Exception {
    Peer op = peer("operator"),
        citizen = peer("citizen1"),
        other = peer("citizen2"),
        responder = peer("police1");
    JsonNode created = report(citizen);
    String id = created.get("id").asText();
    Envelope event = op.event("INCIDENT_CREATED");
    assertEquals(id, event.payload().get("id").asText());
    assertEquals(1, count("incidents"));
    assertEquals(1, count("incident_events"));
    assertEquals(1, count("processed_requests"));
    JsonNode details = detail(citizen, id);
    assertEquals(1, details.get("timeline").size());
    assertEquals(0, details.get("assignments").size());
    error(other.call("GET_INCIDENT", new RevisionInput(id, 0)), NOT_FOUND);
    error(responder.call("GET_INCIDENT", new RevisionInput(id, 0)), FORBIDDEN);
    other.quiet();
    responder.quiet();
    assertFalse(details.toString().contains("internalNote"));
  }

  @Test
  void canonicalRetryAndChangedContentAreCheckedThroughRealRouter() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1");
    String id = UUID.randomUUID().toString();
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("category", "FIRE");
    first.put("address", "Campus");
    first.put("description", "Fiction");
    Envelope request = Envelope.request("CREATE_INCIDENT", id, first),
        response = citizen.send(request);
    ok(response);
    op.event("INCIDENT_CREATED");
    Map<String, Object> reordered = new LinkedHashMap<>();
    reordered.put("description", "Fiction");
    reordered.put("address", "Campus");
    reordered.put("category", "FIRE");
    assertEquals(response, citizen.send(Envelope.request("CREATE_INCIDENT", id, reordered)));
    assertEquals(1, count("incidents"));
    assertEquals(1, count("incident_events"));
    op.quiet();
    reordered.put("description", "Changed");
    error(citizen.send(Envelope.request("CREATE_INCIDENT", id, reordered)), IDEMPOTENCY_CONFLICT);
  }

  @Test
  void incidentFiltersAndPriorityConflictsWorkOverSockets() throws Exception {
    Peer op = peer("operator"), a = peer("citizen1"), b = peer("citizen2");
    JsonNode first = report(a);
    report(a);
    JsonNode third = report(b);
    String id = first.get("id").asText();
    ok(op.call("SET_PRIORITY", new PriorityUpdateInput(id, Priority.HIGH, 0L)));
    error(op.call("SET_PRIORITY", new PriorityUpdateInput(id, Priority.LOW, 0L)), VERSION_CONFLICT);
    ok(
        op.call(
            "REJECT_INCIDENT",
            new IncidentRejectionInput(third.get("id").asText(), "Fictional duplicate", 0L)));
    assertEquals(
        1, ok(op.call("LIST_INCIDENTS", new IncidentFilter(null, Priority.HIGH, 0, 5))).size());
    assertEquals(
        2,
        ok(op.call("LIST_INCIDENTS", new IncidentFilter(IncidentState.REPORTED, null, 0, 5)))
            .size());
    assertEquals(2, ok(a.call("LIST_INCIDENTS", Map.of())).size());
    assertEquals(1, ok(op.call("LIST_INCIDENTS", new IncidentFilter(null, null, 1, 1))).size());
    error(op.call("LIST_INCIDENTS", new IncidentFilter(null, null, 0, 6)), VALIDATION_ERROR);
    error(a.call("SET_PRIORITY", new PriorityUpdateInput(id, Priority.LOW, 1L)), FORBIDDEN);
  }

  @Test
  void missingRevisionAndPrivilegedFieldsAreRejected() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    String id = report(citizen).get("id").asText();
    error(op.call("SET_PRIORITY", Map.of("id", id, "priority", "HIGH")), VALIDATION_ERROR);
    error(
        police.call("SET_TEAM_AVAILABILITY", Map.of("availability", "UNAVAILABLE")),
        VALIDATION_ERROR);
    error(
        police.call("SET_TEAM_AVAILABILITY", Map.of("availability", "BUSY", "expectedRevision", 0)),
        VALIDATION_ERROR);
    error(
        citizen.call(
            "CREATE_INCIDENT",
            Map.of("category", "FIRE", "address", "a", "description", "b", "citizenId", "another")),
        VALIDATION_ERROR);
    JsonNode team = team(op, "police1");
    error(
        op.call(
            "ASSIGN_TEAM",
            Map.of(
                "incidentId",
                id,
                "teamId",
                team.get("id").asText(),
                "expectedIncidentRevision",
                0)),
        VALIDATION_ERROR);
  }

  @Test
  void threeServicesReserveTeamsAndOfferEventsArePrivate() throws Exception {
    Peer op = peer("operator"),
        citizen = peer("citizen1"),
        other = peer("citizen2"),
        police = peer("police1"),
        ambulance = peer("ambulance1"),
        fire = peer("fire1"),
        unrelated = peer("fire2");
    JsonNode incident = report(citizen);
    String id = incident.get("id").asText();
    Peer[] responders = {police, ambulance, fire};
    String[] names = {"police1", "ambulance1", "fire1"};
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < names.length; i++) {
      JsonNode current = detail(op, id).get("incident"), selected = team(op, names[i]);
      JsonNode assignment = offer(op, current, selected);
      Envelope offered = responders[i].event("ASSIGNMENT_OFFERED");
      assertEquals(assignment.get("id"), offered.payload().get("assignment").get("id"));
      JsonNode own = ok(responders[i].call("LIST_ASSIGNMENTS", Map.of()));
      assertEquals(1, own.size());
      assertEquals(id, own.get(0).get("incident").get("id").asText());
      seen.add(assignment.get("serviceType").asText());
      JsonNode reserved = team(op, names[i]);
      assertEquals("BUSY", reserved.get("availability").asText());
      assertEquals(1, reserved.get("revision").asLong());
    }
    assertEquals(Set.of("POLICE", "AMBULANCE", "FIRE_RESCUE"), seen);
    JsonNode details = detail(citizen, id);
    assertEquals(3, details.get("assignments").size());
    assertEquals("ACTIVE", details.get("incident").get("state").asText());
    assertEquals(3, details.get("incident").get("revision").asLong());
    other.quiet();
    unrelated.quiet();
    assertTrue(ok(unrelated.call("LIST_ASSIGNMENTS", Map.of())).isEmpty());
    error(
        unrelated.call("LIST_ASSIGNMENTS", Map.of("teamId", team(op, "fire1").get("id").asText())),
        VALIDATION_ERROR);
    error(citizen.call("LIST_ASSIGNMENTS", Map.of()), FORBIDDEN);
  }

  @Test
  void offlineUnavailableAndBusyTeamsCannotBeOffered() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1");
    JsonNode incident = report(citizen), selected = team(op, "police1");
    error(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(incident.get("id").asText(), selected.get("id").asText(), 0L, 0L)),
        TEAM_UNAVAILABLE);
    Peer police = peer("police1");
    ok(
        police.call(
            "SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(Availability.UNAVAILABLE, 0L)));
    selected = team(op, "police1");
    assertEquals(
        1,
        ok(op.call(
                "LIST_TEAMS",
                new TeamFilter(ServiceType.POLICE, Availability.UNAVAILABLE, Presence.ONLINE)))
            .size());
    error(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(incident.get("id").asText(), selected.get("id").asText(), 0L, 1L)),
        TEAM_UNAVAILABLE);
    error(
        police.call("SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(Availability.AVAILABLE, 0L)),
        VERSION_CONFLICT);
    ok(police.call("SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(Availability.AVAILABLE, 1L)));
    error(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(incident.get("id").asText(), selected.get("id").asText(), 0L, 0L)),
        VERSION_CONFLICT);
    offer(op, incident, team(op, "police1"));
    error(
        police.call(
            "SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(Availability.UNAVAILABLE, 3L)),
        ACTIVE_ASSIGNMENT_EXISTS);
    JsonNode second = report(citizen);
    error(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(second.get("id").asText(), selected.get("id").asText(), 0L, 3L)),
        TEAM_UNAVAILABLE);
  }

  @Test
  void cancellationReleasesReservationAndNeverLeaksPrivateReason() throws Exception {
    Peer op = peer("operator"),
        citizen = peer("citizen1"),
        fire = peer("fire1"),
        replacement = peer("fire2");
    JsonNode incident = report(citizen);
    String id = incident.get("id").asText();
    JsonNode assignment = offer(op, incident, team(op, "fire1"));
    fire.event("ASSIGNMENT_OFFERED");
    String secret = "PRIVATE-INTERNAL-reason-123";
    JsonNode current = detail(op, id).get("incident"), reserved = team(op, "fire1");
    var request =
        Envelope.request(
            "CANCEL_ASSIGNMENT",
            UUID.randomUUID().toString(),
            cancelInput(assignment, current, reserved, secret));
    Envelope response = op.send(request);
    ok(response);
    assertEquals(response, op.send(request));
    assertEquals("AVAILABLE", team(op, "fire1").get("availability").asText());
    JsonNode publicDetails = detail(citizen, id);
    assertEquals("ACTIVE", publicDetails.get("incident").get("state").asText());
    assertEquals("CANCELLED", publicDetails.get("assignments").get(0).get("state").asText());
    assertFalse(publicDetails.toString().contains(secret));
    assertFalse(fire.event("ASSIGNMENT_UPDATED").toString().contains(secret));
    assertFalse(citizen.event("ASSIGNMENT_UPDATED").toString().contains(secret));
    assertEquals(
        secret,
        db.call(
            c -> {
              try (var p =
                  c.prepareStatement(
                      "SELECT internal_note FROM incident_events WHERE assignment_id=? AND"
                          + " event_type='ASSIGNMENT_CANCELLED'")) {
                p.setString(1, assignment.get("id").asText());
                try (var rows = p.executeQuery()) {
                  assertTrue(rows.next());
                  return rows.getString(1);
                }
              }
            }));
    error(
        op.call(
            "REJECT_INCIDENT",
            new IncidentRejectionInput(id, "Cannot reject assignment history", 2L)),
        INVALID_TRANSITION);
    offer(op, publicDetails.get("incident"), team(op, "fire2"));
    replacement.event("ASSIGNMENT_OFFERED");
    assertEquals(2, count("assignments"));
  }

  @Test
  void acceptedCancellationAllowedButEnRouteArrivedAndTerminalRefused() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen), assignment = offer(op, incident, team(op, "police1"));
    String id = assignment.get("id").asText();
    var cancellation =
        cancelInput(
            assignment,
            detail(op, incident.get("id").asText()).get("incident"),
            team(op, "police1"),
            "Fixture reason");
    for (String state : List.of("EN_ROUTE", "ARRIVED", "COMPLETED", "DECLINED", "CANCELLED")) {
      fixtureState(id, state);
      error(op.call("CANCEL_ASSIGNMENT", cancellation), INVALID_TRANSITION);
      assertEquals("BUSY", team(op, "police1").get("availability").asText());
    }
    fixtureState(id, "ACCEPTED");
    assertEquals("CANCELLED", ok(op.call("CANCEL_ASSIGNMENT", cancellation)).get("state").asText());
  }

  @Test
  void cancellationChecksAllExpectedRevisionsAndReason() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen), assignment = offer(op, incident, team(op, "police1"));
    String id = assignment.get("id").asText();
    error(
        op.call("CANCEL_ASSIGNMENT", new AssignmentCancellationInput(id, "valid", 1L, 1L, 1L)),
        VERSION_CONFLICT);
    error(
        op.call("CANCEL_ASSIGNMENT", new AssignmentCancellationInput(id, "valid", 0L, 0L, 1L)),
        VERSION_CONFLICT);
    error(
        op.call("CANCEL_ASSIGNMENT", new AssignmentCancellationInput(id, "valid", 0L, 1L, 0L)),
        VERSION_CONFLICT);
    error(
        op.call("CANCEL_ASSIGNMENT", new AssignmentCancellationInput(id, " ", 0L, 1L, 1L)),
        VALIDATION_ERROR);
    error(
        op.call(
            "CANCEL_ASSIGNMENT", new AssignmentCancellationInput(id, "x".repeat(501), 0L, 1L, 1L)),
        VALIDATION_ERROR);
    assertEquals(
        "OFFERED",
        detail(op, incident.get("id").asText()).get("assignments").get(0).get("state").asText());
  }

  @Test
  void neverAssignedRejectionIsTerminalAndClosureRemainsReserved() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1");
    JsonNode incident = report(citizen);
    String id = incident.get("id").asText();
    error(
        op.call(
            "CLOSE_INCIDENT",
            Map.of("incidentId", id, "expectedRevision", 0, "resolutionReason", "Premature")),
        INVALID_TRANSITION);
    error(op.call("REJECT_INCIDENT", new IncidentRejectionInput(id, " ", 0L)), VALIDATION_ERROR);
    assertEquals(
        "REJECTED",
        ok(op.call("REJECT_INCIDENT", new IncidentRejectionInput(id, "Public duplicate", 0L)))
            .get("state")
            .asText());
    assertTrue(detail(citizen, id).toString().contains("Public duplicate"));
    error(
        op.call("SET_PRIORITY", new PriorityUpdateInput(id, Priority.HIGH, 1L)),
        INVALID_TRANSITION);
  }

  @Test
  void rejectionChecksHistoricalAssignmentsEvenWithReportedFixture() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen);
    offer(op, incident, team(op, "police1"));
    String id = incident.get("id").asText();
    db.transaction(
        c -> {
          try (var p = c.prepareStatement("UPDATE incidents SET state='REPORTED' WHERE id=?")) {
            p.setString(1, id);
            p.executeUpdate();
          }
          return null;
        });
    error(
        op.call("REJECT_INCIDENT", new IncidentRejectionInput(id, "Must reject fixture", 1L)),
        INVALID_TRANSITION);
  }

  @Test
  void concurrentOperatorsCannotReserveSameTeam() throws Exception {
    operator2();
    Peer op1 = peer("operator"),
        op2 = peer("operator2"),
        citizen = peer("citizen1"),
        police = peer("police1");
    JsonNode a = report(citizen), b = report(citizen), team = team(op1, "police1");
    List<Envelope> results =
        compete(
            op1,
            new AssignmentInput(a.get("id").asText(), team.get("id").asText(), 0L, 0L),
            op2,
            new AssignmentInput(b.get("id").asText(), team.get("id").asText(), 0L, 0L));
    assertEquals(1, results.stream().filter(e -> Boolean.TRUE.equals(e.ok())).count());
    assertEquals(1, count("assignments"));
    assertEquals("BUSY", team(op1, "police1").get("availability").asText());
  }

  @Test
  void concurrentSameIncidentServiceOffersCannotBothCommit() throws Exception {
    operator2();
    Peer op1 = peer("operator"),
        op2 = peer("operator2"),
        citizen = peer("citizen1"),
        fire1 = peer("fire1"),
        fire2 = peer("fire2");
    JsonNode incident = report(citizen), a = team(op1, "fire1"), b = team(op1, "fire2");
    String id = incident.get("id").asText();
    List<Envelope> results =
        compete(
            op1,
            new AssignmentInput(id, a.get("id").asText(), 0L, 0L),
            op2,
            new AssignmentInput(id, b.get("id").asText(), 0L, 0L));
    assertEquals(1, results.stream().filter(e -> Boolean.TRUE.equals(e.ok())).count());
    assertEquals(1, count("assignments"));
    JsonNode free =
        team(op1, "fire1").get("availability").asText().equals("AVAILABLE")
            ? team(op1, "fire1")
            : team(op1, "fire2");
    error(
        op1.call("ASSIGN_TEAM", new AssignmentInput(id, free.get("id").asText(), 1L, 0L)),
        ACTIVE_ASSIGNMENT_EXISTS);
  }

  List<Envelope> compete(Peer a, AssignmentInput x, Peer b, AssignmentInput y) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      Future<Envelope> first =
          pool.submit(
              () -> {
                barrier.await(3, TimeUnit.SECONDS);
                return a.call("ASSIGN_TEAM", x);
              });
      Future<Envelope> second =
          pool.submit(
              () -> {
                barrier.await(3, TimeUnit.SECONDS);
                return b.call("ASSIGN_TEAM", y);
              });
      return List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedTimelineWriteRollsBackReportAndProcessedRequest() throws Exception {
    Peer citizen = peer("citizen1");
    db.transaction(
        c -> {
          try (var s = c.createStatement()) {
            s.execute(
                "CREATE TRIGGER reject_timeline BEFORE INSERT ON incident_events BEGIN SELECT"
                    + " RAISE(ABORT,'fixture failure'); END");
          }
          return null;
        });
    error(citizen.call("CREATE_INCIDENT", input()), INTERNAL_ERROR);
    assertEquals(0, count("incidents"));
    assertEquals(0, count("processed_requests"));
  }

  @Test
  void failedOfferRollsBackReservationIncidentAndDedupRecord() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen), team = team(op, "police1");
    op.event("INCIDENT_CREATED");
    db.transaction(
        c -> {
          try (var s = c.createStatement()) {
            s.execute(
                "CREATE TRIGGER reject_offer_timeline BEFORE INSERT ON incident_events BEGIN SELECT"
                    + " RAISE(ABORT,'fixture failure'); END");
          }
          return null;
        });
    error(
        op.call(
            "ASSIGN_TEAM",
            new AssignmentInput(incident.get("id").asText(), team.get("id").asText(), 0L, 0L)),
        INTERNAL_ERROR);
    assertEquals(0, count("assignments"));
    assertEquals(1, count("processed_requests"));
    assertEquals("AVAILABLE", team(op, "police1").get("availability").asText());
    assertEquals(
        "REPORTED",
        detail(citizen, incident.get("id").asText()).get("incident").get("state").asText());
    police.quiet();
  }

  @Test
  void busyReservationSurvivesDisconnectAndDatabaseRestart() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen);
    offer(op, incident, team(op, "police1"));
    police.close();
    for (int i = 0; i < 40 && team(op, "police1").get("presence").asText().equals("ONLINE"); i++)
      Thread.sleep(10);
    assertEquals("BUSY", team(op, "police1").get("availability").asText());
    assertEquals("OFFLINE", team(op, "police1").get("presence").asText());
    for (Peer p : peers) p.close();
    peers.clear();
    server.close();
    db.close();
    db = new Database(dir.resolve("test.db"));
    auth = new AuthService(db);
    server = new TcpServer("127.0.0.1", 0, 32, db, auth);
    Peer fresh = peer("operator");
    assertEquals("BUSY", team(fresh, "police1").get("availability").asText());
    assertEquals("OFFLINE", team(fresh, "police1").get("presence").asText());
    assertEquals(1, detail(fresh, incident.get("id").asText()).get("assignments").size());
  }

  @Test
  void savedMutationReplaysAfterCommitAndClientDropsUnreadAcknowledgement() throws Exception {
    Peer citizen = peer("citizen1");
    Envelope request = Envelope.request("CREATE_INCIDENT", UUID.randomUUID().toString(), input());
    FrameCodec.write(citizen.socket.getOutputStream(), request);
    for (int i = 0; i < 100 && count("processed_requests") == 0; i++) Thread.sleep(20);
    assertEquals(1, count("processed_requests"));
    citizen.close();
    // No client read consumed the acknowledgement. It may already be buffered by TCP.
    Peer reauthenticated = peer("citizen1");
    JsonNode replayed = ok(reauthenticated.send(request));
    assertEquals(1, count("incidents"));
    assertEquals(
        replayed.get("id"),
        detail(reauthenticated, replayed.get("id").asText()).get("incident").get("id"));
  }

  @Test
  void publicTimelinePagingPreservesEventsAndFrameLimit() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1");
    JsonNode incident =
        ok(
            citizen.call(
                "CREATE_INCIDENT",
                new IncidentInput(Category.OTHER, "Campus", "🚑".repeat(1000), null, null)));
    String id = incident.get("id").asText();
    for (long revision = 0; revision < 22; revision++)
      ok(
          op.call(
              "SET_PRIORITY",
              new PriorityUpdateInput(
                  id, revision % 2 == 0 ? Priority.HIGH : Priority.LOW, revision)));
    Set<String> events = new HashSet<>();
    for (int offset = 0; offset < 23; offset += 10) {
      JsonNode page = ok(citizen.call("GET_INCIDENT", new IncidentQuery(id, null, 0, offset)));
      for (JsonNode event : page.get("timeline")) assertTrue(events.add(event.get("id").asText()));
      assertTrue(Json.MAPPER.writeValueAsBytes(page).length < Validation.MAX_FRAME);
    }
    assertEquals(23, events.size());
    error(citizen.call("GET_INCIDENT", new IncidentQuery(id, null, 0, -1)), VALIDATION_ERROR);
  }

  @Test
  void canonicalFingerprintHasStableGoldenEncoding() throws Exception {
    String expected =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(
                        "{\"payload\":{\"a\":1,\"b\":2},\"type\":\"SAMPLE\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertEquals(expected, MutationSupport.hash("SAMPLE", Json.tree(Map.of("b", 2, "a", 1))));
  }

  @Test
  void offerReplayDoesNotRepeatReservationOrRecipientEvents() throws Exception {
    Peer op = peer("operator"), citizen = peer("citizen1"), police = peer("police1");
    JsonNode incident = report(citizen), team = team(op, "police1");
    Envelope request =
        Envelope.request(
            "ASSIGN_TEAM",
            UUID.randomUUID().toString(),
            new AssignmentInput(incident.get("id").asText(), team.get("id").asText(), 0L, 0L));
    Envelope first = op.send(request);
    ok(first);
    police.event("ASSIGNMENT_OFFERED");
    police.event("TEAM_UPDATED");
    JsonNode update = citizen.event("INCIDENT_UPDATED").payload();
    assertEquals("POLICE", update.get("assignment").get("serviceType").asText());
    assertEquals(first, op.send(request));
    police.quiet();
    citizen.quiet();
    assertEquals(1, count("assignments"));
    assertEquals(2, count("processed_requests"));
    assertEquals(1, team(op, "police1").get("revision").asLong());
  }
}
