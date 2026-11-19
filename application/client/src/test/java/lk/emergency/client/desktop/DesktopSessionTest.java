package lk.emergency.client.desktop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import lk.emergency.client.recovery.LocalJournal;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.common.Types.*;
import lk.emergency.server.data.Database;
import lk.emergency.server.network.TcpServer;
import lk.emergency.server.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DesktopSessionTest {
  @TempDir Path directory;
  Database db;
  TcpServer server;
  List<DesktopSession> clients = new ArrayList<>();
  AtomicInteger events = new AtomicInteger();

  @BeforeEach
  void start() throws Exception {
    db = new Database(directory.resolve("desktop.db"));
    var auth = new AuthService(db);
    auth.seed();
    server = new TcpServer("0.0.0.0", 0, 32, db, auth);
  }

  @AfterEach
  void stop() {
    clients.forEach(DesktopSession::close);
    server.close();
    db.close();
  }

  DesktopSession login(String username) throws Exception {
    var client =
        new DesktopSession(
            "127.0.0.1",
            server.port(),
            directory.resolve("journals"),
            text -> {},
            event -> events.incrementAndGet());
    clients.add(client);
    client.login(username, "DemoPass123!");
    return client;
  }

  String ownTeam(DesktopSession client) throws Exception {
    return client.teams().get(0).path("id").asText();
  }

  @Test
  void desktopActionsCompleteThreeServiceWorkflowAndKeepPrivateNotesPrivate() throws Exception {
    var op = login("operator");
    var citizen = login("citizen1");
    var police = login("police1");
    var ambulance = login("ambulance1");
    var fire = login("fire1");
    String id =
        citizen
            .report(Category.ROAD_ACCIDENT, "Fictional UI campus", "Desktop test, no real dispatch")
            .path("id")
            .asText();
    assertTrue(id.matches("\\d{5}"));
    op.priority(id, Priority.HIGH);
    for (var responder : List.of(police, ambulance, fire)) {
      var offer = op.assign(id, ownTeam(responder));
      String aid = offer.path("id").asText();
      assertTrue(
          responder.assignments(true).isEmpty(),
          "An active offer belongs in My assignments, not terminal History");
      responder.respond(aid, true, "PRIVATE-ACCEPT-NOTE");
      responder.progress(aid, "PRIVATE-PROGRESS-NOTE");
      responder.progress(aid, "");
      responder.progress(aid, "");
      assertEquals(
          "COMPLETED", responder.assignmentDetails(aid).path("assignment").path("state").asText());
    }
    assertEquals(3, citizen.incidentDetails(id).path("assignments").size());
    assertTrue(
        citizen.incidentDetails(id).path("timeline").size() > 10,
        "Details must collect later timeline pages");
    assertFalse(citizen.incidentDetails(id).toString().contains("PRIVATE-"));
    assertTrue(op.incidentDetails(id).toString().contains("PRIVATE-PROGRESS-NOTE"));
    op.closeIncident(id, "Fictional exercise completed");
    assertEquals("CLOSED", citizen.incidents(true, "").get(0).path("state").asText());
    assertTrue(citizen.incidents(false, "").isEmpty());
    assertEquals(1, fire.assignments(true).size());
    assertThrows(
        Exception.class,
        () -> fire.read("LIST_INCIDENTS", new IncidentFilter(null, null, 0, 5, false)));
    assertTrue(events.get() > 0);
    assertTrue(citizen.saved().isEmpty());
  }

  @Test
  void draftAndUncertainRequestSurviveNewDesktopSessionAndRetryOnce() throws Exception {
    var citizen = login("citizen1");
    String owner = citizen.user().id();
    citizen.saveDraft(
        new LocalJournal.Draft("FIRE", "Saved fictional lab", "Unsent desktop draft"));
    citizen.close();
    var recovered = login("citizen1");
    assertEquals("Saved fictional lab", recovered.draft().address());
    var journal = new LocalJournal(directory.resolve("journals"), "127.0.0.1:" + server.port());
    var request =
        Envelope.request(
            "CREATE_INCIDENT",
            UUID.randomUUID().toString(),
            new IncidentInput(
                Category.FIRE, "Saved fictional lab", "Uncertain request fixture", null, null));
    journal.put(owner, request);
    assertEquals(1, recovered.saved().size());
    assertThrows(
        Exception.class,
        () ->
            recovered.report(Category.OTHER, "duplicate", "Must be blocked until reconciliation"));
    recovered.retry(request.requestId());
    assertTrue(recovered.saved().isEmpty());
    assertEquals(1, recovered.incidents(false, "").size());
    assertThrows(Exception.class, () -> recovered.retry(request.requestId()));
  }

  @Test
  void desktopListsAllPagesAndEnforcesOwnershipAndAssignmentChoices() throws Exception {
    var citizen = login("citizen1");
    var another = login("citizen2");
    var op = login("operator");
    var fire = login("fire1");
    for (int i = 0; i < 6; i++)
      citizen.report(Category.OTHER, "Fictional room " + i, "Pagination test " + i);
    assertEquals(6, citizen.incidents(false, "").size());
    assertEquals(1, citizen.incidents(false, "room 5").size());
    assertTrue(another.incidents(false, "").isEmpty());
    String id = citizen.incidents(false, "").get(0).path("id").asText();
    assertThrows(Exception.class, () -> another.incidentDetails(id));
    var assignment = op.assign(id, ownTeam(fire));
    String aid = assignment.path("id").asText();
    assertThrows(Exception.class, () -> fire.progress(aid, "Cannot advance an unaccepted offer"));
    fire.respond(aid, false, "PRIVATE-DECLINE");
    String untouched = citizen.incidents(false, "").get(1).path("id").asText();
    op.reject(untouched, "No service required for this fixture");
    assertEquals("REJECTED", citizen.incident(untouched).path("state").asText());
    assertFalse(citizen.incidentDetails(id).toString().contains("PRIVATE-DECLINE"));
    fire.availability(Availability.UNAVAILABLE);
    assertEquals("UNAVAILABLE", fire.teams().get(0).path("availability").asText());
  }
}
