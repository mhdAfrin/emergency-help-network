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

public final class ResponderActions extends ActionSupport {
  public ResponderActions(ClientContext context) {
    super(context);
  }

  public void availability() throws IOException, TimeoutException {
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

  public JsonNode currentOffer() throws IOException, TimeoutException {
    JsonNode offers = read("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5));
    if (offers.isEmpty()) {
      io.print("No active assignment.");
      return null;
    }
    JsonNode offer = offers.get(0);
    io.result(offer);
    return offer;
  }

  public void respond(boolean accept) throws IOException, TimeoutException {
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

  public void progress() throws IOException, TimeoutException {
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

  public void assignmentTimeline() throws IOException, TimeoutException {
    String id = io.ask("Own assignment ID: ").strip();
    for (int offset = 0; ; offset += 10) {
      JsonNode page = read("GET_ASSIGNMENT", new AssignmentQuery(id, offset));
      io.result(page);
      if (!page.get("more").asBoolean()
          || !io.ask("N next timeline page, Enter to return: ").equalsIgnoreCase("n")) break;
    }
  }

  public void current() throws IOException, TimeoutException {
    io.result(read("LIST_ASSIGNMENTS", new AssignmentFilter(0, 5)));
  }

  public void decision() throws IOException, TimeoutException {
    io.heading("RESPOND TO OFFER");
    io.print("  1  Accept\n  2  Decline\n  0  Back");
    switch (io.ask("Choose: ").strip()) {
      case "1" -> respond(true);
      case "2" -> respond(false);
      case "0" -> {}
      default -> io.print("Choose a displayed number.");
    }
  }

  public void teamMenu() throws IOException, TimeoutException {
    io.result(read("LIST_TEAMS", Map.of()));
    io.print("  1  Change availability\n  0  Back");
    if (io.ask("Choose: ").strip().equals("1")) availability();
  }
}
