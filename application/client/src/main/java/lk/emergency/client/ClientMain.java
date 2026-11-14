package lk.emergency.client;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

/** Menu/input stays on main; ConnectionManager owns the independent reader/writer. */
public final class ClientMain {
  private final ConsoleIO io;
  private final ConnectionManager connection;
  private final PendingActions pending;
  private UserView user;

  private ClientMain(ConsoleIO io, ConnectionManager connection) {
    this.io = io;
    this.connection = connection;
    pending = new PendingActions(connection);
  }

  public static void main(String[] args) {
    System.setOut(new PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(new PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    ConsoleIO io = new ConsoleIO();
    io.print(Validation.DISCLAIMER);
    try {
      String host = io.ask("Server host [127.0.0.1]: ").strip();
      if (host.isEmpty()) host = "127.0.0.1";
      String raw = io.ask("Server port [5050]: ").strip();
      int port = raw.isEmpty() ? 5050 : Integer.parseInt(raw);
      try (ConnectionManager connection = new ConnectionManager(host, port, io::event)) {
        new ClientMain(io, connection).run();
      }
    } catch (EOFException e) {
      /* Normal console EOF. */
    } catch (Exception e) {
      io.print("Client stopped: " + e.getMessage());
    }
  }

  private void run() throws IOException {
    while (!connection.isClosed()) {
      menu();
      String action = io.ask("> ").strip();
      if (action.equals("0")) break;
      try {
        if (user == null) startup(action);
        else authenticated(action);
      } catch (TimeoutException e) {
        io.print(e.getMessage());
      } catch (RemoteFailure e) {
        io.print(e.code + ": " + e.getMessage());
        if (e.code == ErrorCode.UNAUTHENTICATED) user = null;
      } catch (IllegalArgumentException e) {
        io.print("Invalid choice or numeric value; review the displayed options.");
      }
    }
    if (connection.isClosed())
      io.print(
          "Disconnected. Automatic reconnect is reserved for Member 3. Unconfirmed outcomes must be"
              + " reconciled, not reported as failed.");
  }

  private void menu() {
    if (user == null) {
      io.print("\n1 Register citizen | 2 Login | 3 Ping | 0 Exit");
      return;
    }
    String common = "\n" + user.displayName() + " [" + user.role() + "] — 1 My identity | 2 Ping";
    String actions =
        switch (user.role()) {
          case CITIZEN ->
              " | 3 Report incident | 4 My incidents/filter | 5 Details/services/timeline";
          case OPERATOR ->
              " | 3 Teams/filter | 4 Private test event | 5 Incidents/filter | 6 Details/timeline |"
                  + " 7 Set priority | 8 Assign team | 10 Cancel assignment | 11 Reject report";
          case RESPONDER -> " | 3 My team | 4 Set availability | 5 My offers/current assignment";
        };
    io.print(common + actions + " | 90 Pending actions | 9 Logout | 0 Exit");
  }

  private void startup(String action) throws IOException, TimeoutException {
    switch (action) {
      case "1" ->
          io.result(
              read(
                  "REGISTER_CITIZEN",
                  new Registration(
                      io.ask("Username: "),
                      io.ask("Display name: "),
                      io.password(),
                      io.ask("Synthetic contact (optional): "))));
      case "2" -> {
        JsonNode response = read("LOGIN", new Login(io.ask("Username: "), io.password()));
        user = Json.MAPPER.treeToValue(response, UserView.class);
        io.result(response);
      }
      case "3" -> io.result(read("PING", Map.of()));
      default -> io.print("Choose a displayed action.");
    }
  }

  private void authenticated(String action) throws IOException, TimeoutException {
    if (action.equals("1")) {
      io.result(read("WHO_AM_I", Map.of()));
      return;
    }
    if (action.equals("2")) {
      io.result(read("PING", Map.of()));
      return;
    }
    if (action.equals("9")) {
      io.result(read("LOGOUT", Map.of()));
      user = null;
      return;
    }
    if (action.equals("90")) {
      pending();
      return;
    }
    switch (user.role()) {
      case CITIZEN -> {
        switch (action) {
          case "3" -> report();
          case "4" -> listIncidents();
          case "5" -> details();
          default -> io.print("Choose a displayed action.");
        }
      }
      case OPERATOR -> {
        switch (action) {
          case "3" -> listTeams();
          case "4" -> io.result(read("TEST_EVENT", Map.of()));
          case "5" -> listIncidents();
          case "6" -> details();
          case "7" -> priority();
          case "8" -> assign();
          case "10" -> cancel();
          case "11" -> reject();
          default -> io.print("Choose a displayed action.");
        }
      }
      case RESPONDER -> {
        switch (action) {
          case "3" -> io.result(read("LIST_TEAMS", Map.of()));
          case "4" -> availability();
          case "5" -> io.result(read("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5)));
          default -> io.print("Choose a displayed action.");
        }
      }
    }
  }

  private JsonNode read(String type, Object payload) throws IOException, TimeoutException {
    return checked(connection.call(type, payload)).payload();
  }

  private Envelope checked(Envelope response) {
    if (!Boolean.TRUE.equals(response.ok()))
      throw new RemoteFailure(response.error().code(), response.error().message());
    return response;
  }

  private void mutate(String type, Object payload) throws IOException, TimeoutException {
    Envelope response = checked(pending.submit(user.id(), type, payload));
    showMutation(response);
  }

  private void showMutation(Envelope response) {
    io.result(response.payload());
    if (response.type().equals("CREATE_INCIDENT"))
      io.print("Confirmed incident reference: " + response.payload().get("id").asText());
  }

  private void report() throws IOException, TimeoutException {
    io.print(Validation.DISCLAIMER);
    io.print("Categories: MEDICAL, ROAD_ACCIDENT, FIRE, CRIME, OTHER");
    Category category = Category.valueOf(io.ask("Category: ").strip().toUpperCase(Locale.ROOT));
    String address = io.ask("Address/landmark: "), description = io.ask("Description: ");
    String lat = io.ask("Latitude (optional; Enter to omit): ").strip(),
        lon = io.ask("Longitude (optional; Enter to omit): ").strip();
    mutate(
        "CREATE_INCIDENT",
        new IncidentInput(
            category,
            address,
            description,
            lat.isEmpty() ? null : Double.valueOf(lat),
            lon.isEmpty() ? null : Double.valueOf(lon)));
  }

  private void listIncidents() throws IOException, TimeoutException {
    IncidentState state =
        optionalEnum(IncidentState.class, "State REPORTED/ACTIVE/CLOSED/REJECTED (Enter=all): ");
    Priority priority = optionalEnum(Priority.class, "Priority LOW/MEDIUM/HIGH (Enter=all): ");
    for (int offset = 0; ; offset += 5) {
      JsonNode page = read("LIST_INCIDENTS", new IncidentFilter(state, priority, offset, 5));
      io.result(page);
      if (page.size() < 5 || !io.ask("N for next page, Enter to return: ").equalsIgnoreCase("n"))
        break;
    }
  }

  private void listTeams() throws IOException, TimeoutException {
    ServiceType service =
        optionalEnum(ServiceType.class, "Service POLICE/AMBULANCE/FIRE_RESCUE (Enter=all): ");
    Availability available =
        optionalEnum(Availability.class, "Availability AVAILABLE/BUSY/UNAVAILABLE (Enter=all): ");
    Presence presence = optionalEnum(Presence.class, "Presence ONLINE/OFFLINE (Enter=all): ");
    io.result(read("LIST_TEAMS", new TeamFilter(service, available, presence)));
  }

  private void details() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    int a = 0, t = 0;
    while (true) {
      JsonNode response = read("GET_INCIDENT", new IncidentQuery(id, null, a, t));
      io.result(response.get("incident"));
      io.print("Separate service assignments:");
      for (JsonNode service : response.get("assignments"))
        io.print(
            service.get("serviceType").asText()
                + ": "
                + service.get("state").asText()
                + " | assignment "
                + service.get("id").asText()
                + " | revision "
                + service.get("revision"));
      io.print("Public timeline:");
      io.result(response.get("timeline"));
      boolean moreA = response.get("moreAssignments").asBoolean(),
          moreT = response.get("moreTimeline").asBoolean();
      if (!moreA && !moreT) break;
      String next =
          io.ask(
                  (moreA ? "A next assignments | " : "")
                      + (moreT ? "T next timeline | " : "")
                      + "Enter to return: ")
              .strip();
      if (moreA && next.equalsIgnoreCase("a")) a += 10;
      else if (moreT && next.equalsIgnoreCase("t")) t += 10;
      else break;
    }
  }

  private JsonNode incident(String id) throws IOException, TimeoutException {
    return read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0)).get("incident");
  }

  private void priority() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode current = incident(id);
    io.result(current);
    Priority next =
        Priority.valueOf(io.ask("Priority LOW/MEDIUM/HIGH: ").strip().toUpperCase(Locale.ROOT));
    if (confirm())
      mutate("SET_PRIORITY", new PriorityUpdateInput(id, next, current.get("revision").asLong()));
  }

  private JsonNode team(String id) throws IOException, TimeoutException {
    for (JsonNode team : read("LIST_TEAMS", Map.of()))
      if (team.get("id").asText().equals(id)) return team;
    throw new IllegalArgumentException("Unknown team");
  }

  private void assign() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode incident = incident(id);
    io.result(incident);
    io.result(read("LIST_TEAMS", Map.of()));
    String teamId = io.ask("Team ID: ").strip();
    JsonNode team = team(teamId);
    io.result(team);
    if (confirm())
      mutate(
          "ASSIGN_TEAM",
          new AssignmentInput(
              id, teamId, incident.get("revision").asLong(), team.get("revision").asLong()));
  }

  private void cancel() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode details = read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0));
    io.result(details);
    String assignmentId = io.ask("Assignment ID: ").strip();
    JsonNode found = null;
    for (int offset = 0; ; offset += 10) {
      JsonNode page = read("GET_INCIDENT", new IncidentQuery(id, null, offset, 0));
      for (JsonNode row : page.get("assignments"))
        if (row.get("id").asText().equals(assignmentId)) found = row;
      if (found != null || !page.get("moreAssignments").asBoolean()) break;
    }
    if (found == null) {
      io.print("Assignment not found on this incident.");
      return;
    }
    JsonNode current = incident(id), team = team(found.get("teamId").asText());
    io.result(found);
    io.result(team);
    String reason = io.ask("Private cancellation reason (required): ");
    if (confirm())
      mutate(
          "CANCEL_ASSIGNMENT",
          new AssignmentCancellationInput(
              assignmentId,
              reason,
              found.get("revision").asLong(),
              current.get("revision").asLong(),
              team.get("revision").asLong()));
  }

  private void reject() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode current = incident(id);
    io.result(current);
    String reason = io.ask("Public rejection reason (required): ");
    if (confirm())
      mutate(
          "REJECT_INCIDENT",
          new IncidentRejectionInput(id, reason, current.get("revision").asLong()));
  }

  private void availability() throws IOException, TimeoutException {
    JsonNode list = read("LIST_TEAMS", Map.of());
    if (list.isEmpty()) return;
    JsonNode team = list.get(0);
    io.result(team);
    if (team.get("availability").asText().equals("BUSY")) {
      io.print("An active assignment reserves your team.");
      return;
    }
    Availability next =
        Availability.valueOf(
            io.ask("Availability AVAILABLE/UNAVAILABLE: ").strip().toUpperCase(Locale.ROOT));
    if (confirm())
      mutate(
          "SET_TEAM_AVAILABILITY", new TeamAvailabilityInput(next, team.get("revision").asLong()));
  }

  private void pending() throws IOException, TimeoutException {
    var tickets = pending.list(user.id());
    if (tickets.isEmpty()) {
      io.print("No pending actions.");
      return;
    }
    for (var ticket : tickets)
      io.print(
          ticket.request().requestId()
              + " | "
              + ticket.request().type()
              + " | "
              + (ticket.response().isDone() ? "Outcome available" : "Confirmation pending"));
    String id = io.ask("Request ID (Enter to return): ").strip();
    if (id.isEmpty()) return;
    var ticket = tickets.stream().filter(x -> x.request().requestId().equals(id)).findFirst();
    if (ticket.isEmpty()) {
      io.print("Unknown pending ID.");
      return;
    }
    io.print("Retained input:");
    io.result(ticket.get().request().payload());
    boolean retry = false;
    if (!ticket.get().response().isDone())
      retry = io.ask("Retry unchanged request with SAME ID? y/N: ").equalsIgnoreCase("y");
    showMutation(checked(pending.resolve(user.id(), id, retry, Duration.ofSeconds(10))));
  }

  private boolean confirm() throws IOException {
    return io.ask("Review the displayed values. Confirm? y/N: ").equalsIgnoreCase("y");
  }

  private <T extends Enum<T>> T optionalEnum(Class<T> type, String prompt) throws IOException {
    String value = io.ask(prompt).strip();
    return value.isEmpty() ? null : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
  }

  private static final class RemoteFailure extends RuntimeException {
    final ErrorCode code;

    RemoteFailure(ErrorCode code, String message) {
      super(message);
      this.code = code;
    }
  }
}
