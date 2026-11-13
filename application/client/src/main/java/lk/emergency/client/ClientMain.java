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
  private final ReconnectingClient connection;
  private final LiveState live;
  private final LocalJournal journal;
  private final PendingActions pending;
  private UserView user;

  private ClientMain(
      ConsoleIO io, ReconnectingClient connection, LiveState live, LocalJournal journal) {
    this.io = io;
    this.connection = connection;
    this.live = live;
    this.journal = journal;
    pending = new PendingActions(connection, journal);
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
      LiveState live = new LiveState();
      LocalJournal journal =
          new LocalJournal(java.nio.file.Path.of(".local/client"), host + ":" + port);
      try (ReconnectingClient connection =
          new ReconnectingClient(
              host,
              port,
              event -> {
                if (live.accept(event)) io.event(event);
              },
              io::print)) {
        new ClientMain(io, connection, live, journal).run();
      }
    } catch (EOFException e) {
      /* Normal console EOF. */
    } catch (Exception e) {
      io.print("Client stopped: " + e.getMessage());
    }
  }

  private void run() throws IOException {
    while (true) {
      if (user != null && !connection.authenticated()) {
        user = null;
        io.print(
            "Login again when the server connection returns. Saved drafts/pending IDs are"
                + " retained.");
      }
      menu();
      String action = io.ask("> ").strip();
      if (action.equals("0")) break;
      try {
        if (user != null && !connection.authenticated()) user = null;
        if (user == null) startup(action);
        else authenticated(action);
      } catch (TimeoutException e) {
        io.print(e.getMessage());
      } catch (RemoteFailure e) {
        io.print(e.code + ": " + e.getMessage());
        if (e.code == ErrorCode.UNAUTHENTICATED) user = null;
      } catch (EOFException e) {
        throw e;
      } catch (IOException e) {
        io.print(e.getMessage());
        if (!connection.authenticated()) user = null;
      } catch (IllegalArgumentException e) {
        io.print("Invalid choice or numeric value; review the displayed options.");
      }
    }
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
              " | 3 Report incident | 4 My active incidents/filter | 5 Details/services/timeline |"
                  + " 6 History";
          case OPERATOR ->
              " | 3 Teams/filter | 4 Private test event | 5 Incidents/filter | 6 Details/timeline |"
                  + " 7 Set priority | 8 Assign team | 10 Cancel assignment | 11 Reject report | 12"
                  + " Close incident | 13 History";
          case RESPONDER ->
              " | 3 My team | 4 Set availability | 5 My offers/current assignment | 6 Accept | 7"
                  + " Decline | 8 Next progress | 10 History | 11 Assignment timeline";
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
        live.begin();
        JsonNode response = read("LOGIN", new Login(io.ask("Username: "), io.password()));
        UserView logged = Json.MAPPER.treeToValue(response, UserView.class);
        try {
          boolean synchronizedState = false;
          for (int attempt = 0; attempt < 3; attempt++) {
            try {
              live.synchronize(connection, logged);
              synchronizedState = true;
              break;
            } catch (IOException e) {
              if (attempt == 2) throw e;
            }
          }
          if (!synchronizedState) throw new IOException("Please log in and synchronize again");
        } catch (IOException | TimeoutException failure) {
          try {
            connection.call("LOGOUT", Map.of());
          } catch (Exception ignored) {
          }
          throw failure;
        }
        user = logged;
        io.result(response);
        io.print("Authorized snapshot synchronized. Recovered service states:");
        for (JsonNode assignment : live.assignments())
          io.print(
              assignment.path("serviceType").asText()
                  + ": "
                  + assignment.path("state").asText()
                  + " | "
                  + assignment.path("id").asText());
        int saved = pending.list(user.id()).size();
        if (saved > 0)
          io.print(
              saved + " saved uncertain action(s). Use menu 90 to reconcile the original IDs.");
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
          case "6" -> history();
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
          case "12" -> closeIncident();
          case "13" -> history();
          default -> io.print("Choose a displayed action.");
        }
      }
      case RESPONDER -> {
        switch (action) {
          case "3" -> io.result(read("LIST_TEAMS", Map.of()));
          case "4" -> availability();
          case "5" -> io.result(read("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5)));
          case "6" -> respond(true);
          case "7" -> respond(false);
          case "8" -> progress();
          case "10" -> history();
          case "11" -> assignmentTimeline();
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
    live.response(response);
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
    if (pending.list(user.id()).stream().anyMatch(t -> t.request().type().equals("CREATE_INCIDENT"))
        && !io.ask(
                "A report is unconfirmed; use menu 90 to reconcile it. Start a different report?"
                    + " y/N: ")
            .equalsIgnoreCase("y")) return;
    io.print(Validation.DISCLAIMER);
    LocalJournal.Draft saved = journal.draft(user.id());
    if (saved != null && !io.ask("Resume your saved unsent report? y/N: ").equalsIgnoreCase("y"))
      saved = null;
    LocalJournal.Draft draft = saved == null ? new LocalJournal.Draft("", "", "", "", "") : saved;
    String category =
        draftValue("Category MEDICAL/ROAD_ACCIDENT/FIRE/CRIME/OTHER", draft.category());
    draft =
        new LocalJournal.Draft(
            category, draft.address(), draft.description(), draft.latitude(), draft.longitude());
    journal.draft(user.id(), draft);
    String address = draftValue("Address/landmark", draft.address());
    draft =
        new LocalJournal.Draft(
            category, address, draft.description(), draft.latitude(), draft.longitude());
    journal.draft(user.id(), draft);
    String description = draftValue("Description", draft.description());
    draft =
        new LocalJournal.Draft(category, address, description, draft.latitude(), draft.longitude());
    journal.draft(user.id(), draft);
    String latitude = draftValue("Latitude (optional)", draft.latitude());
    draft = new LocalJournal.Draft(category, address, description, latitude, draft.longitude());
    journal.draft(user.id(), draft);
    String longitude = draftValue("Longitude (optional)", draft.longitude());
    draft = new LocalJournal.Draft(category, address, description, latitude, longitude);
    journal.draft(user.id(), draft);
    mutate(
        "CREATE_INCIDENT",
        new IncidentInput(
            Category.valueOf(category.toUpperCase(Locale.ROOT)),
            address,
            description,
            latitude.isBlank() ? null : Double.valueOf(latitude),
            longitude.isBlank() ? null : Double.valueOf(longitude)));
  }

  private String draftValue(String name, String saved) throws IOException {
    String value = io.ask(name + (saved.isEmpty() ? ": " : " [Enter keeps saved; - clears]: "));
    return value.equals("-") ? "" : value.isEmpty() ? saved : value;
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
              + (ticket.response().isCompletedExceptionally()
                  ? "Unconfirmed; retry after login"
                  : ticket.response().isDone() ? "Outcome available" : "Confirmation pending"));
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
    if (!ticket.get().response().isDone() || ticket.get().response().isCompletedExceptionally())
      retry = io.ask("Retry unchanged request with SAME ID? y/N: ").equalsIgnoreCase("y");
    showMutation(checked(pending.resolve(user.id(), id, retry, Duration.ofSeconds(10))));
  }

  private JsonNode currentOffer() throws IOException, TimeoutException {
    JsonNode offers = read("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5));
    if (offers.isEmpty()) {
      io.print("No active assignment.");
      return null;
    }
    JsonNode offer = offers.get(0);
    io.result(offer);
    return offer;
  }

  private void respond(boolean accept) throws IOException, TimeoutException {
    JsonNode offer = currentOffer();
    if (offer == null) return;
    JsonNode a = offer.get("assignment"), incident = offer.get("incident");
    if (!a.get("state").asText().equals("OFFERED")) {
      io.print("Only an OFFERED assignment can be accepted or declined.");
      return;
    }
    JsonNode own = read("LIST_TEAMS", Map.of()).get(0);
    String reason =
        io.ask(accept ? "Private note (optional): " : "Private decline reason (required): ");
    if (confirm())
      mutate(
          "RESPOND_ASSIGNMENT",
          new AssignmentResponseInput(
              a.get("id").asText(),
              accept ? AssignmentDecision.ACCEPT : AssignmentDecision.DECLINE,
              reason,
              a.get("revision").asLong(),
              incident.get("revision").asLong(),
              own.get("revision").asLong()));
  }

  private void progress() throws IOException, TimeoutException {
    JsonNode offer = currentOffer();
    if (offer == null) return;
    JsonNode a = offer.get("assignment"), incident = offer.get("incident");
    AssignmentState next =
        switch (AssignmentState.valueOf(a.get("state").asText())) {
          case ACCEPTED -> AssignmentState.EN_ROUTE;
          case EN_ROUTE -> AssignmentState.ARRIVED;
          case ARRIVED -> AssignmentState.COMPLETED;
          default -> null;
        };
    if (next == null) {
      io.print("Accept the offer before sending ordered progress.");
      return;
    }
    io.print("Next valid progress: " + next);
    String note = io.ask("Private progress note (optional): ");
    JsonNode own = read("LIST_TEAMS", Map.of()).get(0);
    if (confirm())
      mutate(
          "UPDATE_ASSIGNMENT_STATUS",
          new AssignmentStatusInput(
              a.get("id").asText(),
              next,
              note,
              a.get("revision").asLong(),
              incident.get("revision").asLong(),
              own.get("revision").asLong()));
  }

  private void closeIncident() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode details = read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0));
    io.result(details);
    String reason = io.ask("Public resolution reason (required): ");
    if (confirm())
      mutate(
          "CLOSE_INCIDENT",
          new IncidentClosureInput(id, reason, details.get("incident").get("revision").asLong()));
  }

  private void history() throws IOException, TimeoutException {
    for (int offset = 0; ; offset += 5) {
      JsonNode page =
          user.role() == Role.RESPONDER
              ? read("LIST_ASSIGNMENTS", new AssignmentFilter(offset, 5, true))
              : read("LIST_INCIDENTS", new IncidentFilter(null, null, offset, 5, true));
      io.result(page);
      if (page.size() < 5
          || !io.ask("N next history page, Enter to return: ").equalsIgnoreCase("n")) break;
    }
  }

  private void assignmentTimeline() throws IOException, TimeoutException {
    String id = io.ask("Own assignment ID: ").strip();
    for (int offset = 0; ; offset += 10) {
      JsonNode page = read("GET_ASSIGNMENT", new AssignmentQuery(id, offset));
      io.result(page);
      if (!page.get("more").asBoolean()
          || !io.ask("N next timeline page, Enter to return: ").equalsIgnoreCase("n")) break;
    }
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
