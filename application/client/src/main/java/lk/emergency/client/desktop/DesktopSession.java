package lk.emergency.client.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.common.Types.*;

/**
 * UI-independent application adapter. All calls run on one background worker, never on Swing's EDT.
 */
public final class DesktopSession implements AutoCloseable {
  private final ReconnectingClient network;
  private final LiveState live = new LiveState();
  private final LocalJournal journal;
  private final PendingActions pending;
  private volatile UserView user;
  public final String endpoint;

  public DesktopSession(
      String host, int port, Path local, Consumer<String> status, Consumer<Envelope> events)
      throws Exception {
    ClientOptions.validateHost(host);
    ClientOptions.parsePort(Integer.toString(port));
    ServerProbe.check(host, port);
    endpoint = host + ":" + port;
    journal = new LocalJournal(local, endpoint);
    network =
        new ReconnectingClient(
            host,
            port,
            event -> {
              if (live.accept(event)) events.accept(event);
            },
            status);
    pending = new PendingActions(network, journal);
  }

  public UserView user() {
    return user;
  }

  public boolean connected() {
    return network.connected();
  }

  public boolean authenticated() {
    return user != null && network.authenticated();
  }

  public void forgetLogin() {
    user = null;
  }

  public UserView login(String username, String password) throws Exception {
    user = null;
    live.begin();
    UserView logged =
        Json.MAPPER.treeToValue(
            read("LOGIN", new Login(username.strip(), password)), UserView.class);
    try {
      live.synchronize(network, logged);
    } catch (Exception failure) {
      try {
        network.call("LOGOUT", Map.of());
      } catch (Exception ignored) {
      }
      throw failure;
    }
    user = logged;
    return logged;
  }

  public JsonNode register(String username, String display, String password, String contact)
      throws Exception {
    return read(
        "REGISTER_CITIZEN",
        new Registration(username.strip(), display.strip(), password, contact.strip()));
  }

  public void logout() throws Exception {
    try {
      read("LOGOUT", Map.of());
    } finally {
      user = null;
    }
  }

  public JsonNode read(String type, Object payload) throws Exception {
    return checked(network.call(type, payload));
  }

  private JsonNode checked(Envelope response) throws IOException {
    if (!Boolean.TRUE.equals(response.ok())) {
      if (response.error() != null && response.error().code() == ErrorCode.UNAUTHENTICATED)
        user = null;
      throw new IOException(
          response.error() == null
              ? "Server returned an invalid response."
              : response.error().message());
    }
    live.response(response);
    return response.payload();
  }

  public JsonNode change(String type, Object payload) throws Exception {
    if (!authenticated()) throw new IOException("Please log in again before changing anything.");
    if (!pending.list(user.id()).isEmpty())
      throw new IOException(
          "An earlier action needs confirmation. Open Saved actions before submitting another"
              + " change.");
    return checked(pending.submit(user.id(), type, payload));
  }

  public JsonNode incidents(boolean history, String search) throws Exception {
    List<JsonNode> rows = new ArrayList<>();
    for (int offset = 0; offset <= 10000; offset += 5) {
      JsonNode page = read("LIST_INCIDENTS", new IncidentFilter(null, null, offset, 5, history));
      page.forEach(rows::add);
      if (page.size() < 5) break;
    }
    var array = Json.MAPPER.createArrayNode();
    String filter = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    for (var row : rows)
      if (filter.isEmpty()
          || (row.path("id").asText()
                  + " "
                  + row.path("address").asText()
                  + " "
                  + row.path("category").asText()
                  + " "
                  + row.path("state").asText())
              .toLowerCase(Locale.ROOT)
              .contains(filter)) array.add(row);
    return array;
  }

  public JsonNode assignments(boolean history) throws Exception {
    var rows = Json.MAPPER.createArrayNode();
    for (int offset = 0; offset <= 10000; offset += 5) {
      JsonNode page = read("LIST_ASSIGNMENTS", new AssignmentFilter(offset, 5, history));
      for (var row : page)
        if (!history
            || Set.of("COMPLETED", "DECLINED", "CANCELLED")
                .contains(row.path("assignment").path("state").asText())) rows.add(row);
      if (page.size() < 5) break;
    }
    return rows;
  }

  public JsonNode teams() throws Exception {
    return read("LIST_TEAMS", Map.of());
  }

  public JsonNode incident(String id) throws Exception {
    return read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0)).path("incident");
  }

  public JsonNode incidentDetails(String id) throws Exception {
    JsonNode first = read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0));
    var merged = first.deepCopy();
    int a = 0, t = 0;
    JsonNode page = first;
    while (page.path("moreAssignments").asBoolean() || page.path("moreTimeline").asBoolean()) {
      boolean moreA = page.path("moreAssignments").asBoolean(),
          moreT = page.path("moreTimeline").asBoolean();
      if (moreA) a += 10;
      if (moreT) t += 10;
      page = read("GET_INCIDENT", new IncidentQuery(id, null, a, t));
      if (page.path("incident").path("revision").asLong()
          != first.path("incident").path("revision").asLong())
        throw new IOException("The incident changed while loading. Open details again.");
      if (moreA)
        page.path("assignments")
            .forEach(
                ((com.fasterxml.jackson.databind.node.ArrayNode) merged.path("assignments"))::add);
      if (moreT) {
        page.path("timeline")
            .forEach(
                ((com.fasterxml.jackson.databind.node.ArrayNode) merged.path("timeline"))::add);
        if (merged.has("operationalNotes"))
          page.path("operationalNotes")
              .forEach(
                  ((com.fasterxml.jackson.databind.node.ArrayNode) merged.path("operationalNotes"))
                      ::add);
      }
      // Once one list is complete its repeated final page must not make it active again.
      if (!moreA)
        ((com.fasterxml.jackson.databind.node.ObjectNode) page).put("moreAssignments", false);
      if (!moreT)
        ((com.fasterxml.jackson.databind.node.ObjectNode) page).put("moreTimeline", false);
    }
    return merged;
  }

  public JsonNode assignmentDetails(String id) throws Exception {
    JsonNode first = read("GET_ASSIGNMENT", new AssignmentQuery(id, 0)), page = first;
    var merged = first.deepCopy();
    int offset = 0;
    while (page.path("more").asBoolean()) {
      page = read("GET_ASSIGNMENT", new AssignmentQuery(id, offset += 10));
      if (page.path("assignment").path("revision").asLong()
          != first.path("assignment").path("revision").asLong())
        throw new IOException("The assignment changed while loading. Open details again.");
      page.path("timeline")
          .forEach(((com.fasterxml.jackson.databind.node.ArrayNode) merged.path("timeline"))::add);
    }
    return merged;
  }

  public JsonNode report(Category category, String address, String description) throws Exception {
    return change("CREATE_INCIDENT", new IncidentInput(category, address, description, null, null));
  }

  private JsonNode team(String id) throws Exception {
    for (JsonNode row : teams()) if (row.path("id").asText().equals(id)) return row;
    throw new IOException("Team is no longer available. Refresh the team list.");
  }

  public JsonNode assign(String incidentId, String teamId) throws Exception {
    var incident = incident(incidentId);
    var team = team(teamId);
    return change(
        "ASSIGN_TEAM",
        new AssignmentInput(
            incidentId,
            teamId,
            incident.path("revision").asLong(),
            team.path("revision").asLong()));
  }

  public JsonNode priority(String id, Priority value) throws Exception {
    return change(
        "SET_PRIORITY", new PriorityUpdateInput(id, value, incident(id).path("revision").asLong()));
  }

  public JsonNode closeIncident(String id, String reason) throws Exception {
    return change(
        "CLOSE_INCIDENT",
        new IncidentClosureInput(id, reason, incident(id).path("revision").asLong()));
  }

  public JsonNode reject(String id, String reason) throws Exception {
    return change(
        "REJECT_INCIDENT",
        new IncidentRejectionInput(id, reason, incident(id).path("revision").asLong()));
  }

  public JsonNode cancel(JsonNode assignment, String reason) throws Exception {
    String incidentId = assignment.path("incidentId").asText();
    JsonNode current = null;
    for (var row : incidentDetails(incidentId).path("assignments"))
      if (row.path("id").equals(assignment.path("id"))) current = row;
    if (current == null) throw new IOException("Assignment no longer exists. Refresh.");
    return change(
        "CANCEL_ASSIGNMENT",
        new AssignmentCancellationInput(
            current.path("id").asText(),
            reason,
            current.path("revision").asLong(),
            incident(incidentId).path("revision").asLong(),
            team(current.path("teamId").asText()).path("revision").asLong()));
  }

  public JsonNode respond(String id, boolean accept, String note) throws Exception {
    var details = assignmentDetails(id);
    var a = details.path("assignment");
    var incident = details.path("incident");
    return change(
        "RESPOND_ASSIGNMENT",
        new AssignmentResponseInput(
            id,
            accept ? AssignmentDecision.ACCEPT : AssignmentDecision.DECLINE,
            note,
            a.path("revision").asLong(),
            incident.path("revision").asLong(),
            team(a.path("teamId").asText()).path("revision").asLong()));
  }

  public JsonNode progress(String id, String note) throws Exception {
    var details = assignmentDetails(id);
    var a = details.path("assignment");
    var incident = details.path("incident");
    AssignmentState next = nextState(a.path("state").asText());
    if (next == null)
      throw new IOException("Accept the offer first; completed assignments cannot advance.");
    return change(
        "UPDATE_ASSIGNMENT_STATUS",
        new AssignmentStatusInput(
            id,
            next,
            note,
            a.path("revision").asLong(),
            incident.path("revision").asLong(),
            team(a.path("teamId").asText()).path("revision").asLong()));
  }

  public JsonNode availability(Availability value) throws Exception {
    JsonNode own = teams().path(0);
    return change(
        "SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(value, own.path("revision").asLong()));
  }

  public static AssignmentState nextState(String state) {
    return switch (state) {
      case "ACCEPTED" -> AssignmentState.EN_ROUTE;
      case "EN_ROUTE" -> AssignmentState.ARRIVED;
      case "ARRIVED" -> AssignmentState.COMPLETED;
      default -> null;
    };
  }

  public List<PendingActions.Ticket> saved() throws IOException {
    return user == null ? List.of() : pending.list(user.id());
  }

  public JsonNode retry(String id) throws Exception {
    return checked(pending.resolve(user.id(), id, true, Duration.ofSeconds(10)));
  }

  public LocalJournal.Draft draft() throws IOException {
    return journal.draft(user.id());
  }

  public void saveDraft(LocalJournal.Draft value) throws IOException {
    journal.draft(user.id(), value);
  }

  @Override
  public void close() {
    user = null;
    network.close();
  }
}
