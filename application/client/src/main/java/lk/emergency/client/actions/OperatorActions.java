package lk.emergency.client.actions;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.client.support.ClientContext;
import lk.emergency.client.ui.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class OperatorActions extends ActionSupport {
  public OperatorActions(ClientContext context) {
    super(context);
  }

  public void listTeams() throws IOException, TimeoutException {
    ServiceType service =
        optionalEnum(ServiceType.class, "Service POLICE/AMBULANCE/FIRE_RESCUE (Enter=all): ");
    Availability available =
        optionalEnum(Availability.class, "Availability AVAILABLE/BUSY/UNAVAILABLE (Enter=all): ");
    Presence presence = optionalEnum(Presence.class, "Presence ONLINE/OFFLINE (Enter=all): ");
    io.result(read("LIST_TEAMS", new TeamFilter(service, available, presence)));
  }

  public void assign() throws IOException, TimeoutException {
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

  public void priority() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode current = incident(id);
    io.result(current);
    Priority next =
        Priority.valueOf(io.ask("Priority LOW/MEDIUM/HIGH: ").strip().toUpperCase(Locale.ROOT));
    if (confirm())
      mutate("SET_PRIORITY", new PriorityUpdateInput(id, next, current.get("revision").asLong()));
  }

  public void cancel() throws IOException, TimeoutException {
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

  public void reject() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode current = incident(id);
    io.result(current);
    String reason = io.ask("Public rejection reason (required): ");
    if (confirm())
      mutate(
          "REJECT_INCIDENT",
          new IncidentRejectionInput(id, reason, current.get("revision").asLong()));
  }

  public void closeIncident() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    JsonNode details = read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0));
    io.result(details);
    String reason = io.ask("Public resolution reason (required): ");
    if (confirm())
      mutate(
          "CLOSE_INCIDENT",
          new IncidentClosureInput(id, reason, details.get("incident").get("revision").asLong()));
  }

  public void manage() throws IOException, TimeoutException {
    io.heading("MANAGE INCIDENT");
    io.print(
        "  1  View details\n"
            + "  2  Change priority\n"
            + "  3  Close incident\n"
            + "  4  Reject an unassigned report\n"
            + "  5  Cancel an eligible assignment\n"
            + "  0  Back");
    switch (io.ask("Choose: ").strip()) {
      case "1" -> new CitizenActions(context).details();
      case "2" -> priority();
      case "3" -> closeIncident();
      case "4" -> reject();
      case "5" -> cancel();
      case "0" -> {}
      default -> io.print("Choose a displayed number.");
    }
  }
}
