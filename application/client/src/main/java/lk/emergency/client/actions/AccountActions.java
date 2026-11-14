package lk.emergency.client.actions;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.client.support.ClientContext;
import lk.emergency.client.ui.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class AccountActions extends ActionSupport {
  public AccountActions(ClientContext context) {
    super(context);
  }

  public void history() throws IOException, TimeoutException {
    for (int offset = 0; ; offset += 5) {
      JsonNode page =
          context.user().role() == Role.RESPONDER
              ? read("LIST_ASSIGNMENTS", new AssignmentFilter(offset, 5, true))
              : read("LIST_INCIDENTS", new IncidentFilter(null, null, offset, 5, true));
      io.result(page);
      if (page.size() < 5
          || !io.ask("N next history page, Enter to return: ").equalsIgnoreCase("n")) break;
    }
  }

  public void pending() throws IOException, TimeoutException {
    var tickets = pending.list(context.user().id());
    if (tickets.isEmpty()) {
      io.print("No pending actions.");
      return;
    }
    for (int index = 0; index < tickets.size(); index++) {
      var action = tickets.get(index);
      io.print(
          "  "
              + (index + 1)
              + "  "
              + RecordPrinter.words(action.request().type())
              + " - "
              + (action.response().isDone() ? "Check outcome" : "Awaiting confirmation"));
    }
    String value = io.ask("Choose saved action number (Enter returns): ").strip();
    if (value.isEmpty()) return;
    int index = Integer.parseInt(value) - 1;
    if (index < 0 || index >= tickets.size())
      throw new IllegalArgumentException("Choose a saved action.");
    String id = tickets.get(index).request().requestId();
    Optional<PendingActions.Ticket> ticket = Optional.of(tickets.get(index));
    io.print("Retained input:");
    io.result(ticket.get().request().payload());
    boolean retry = false;
    if (!ticket.get().response().isDone() || ticket.get().response().isCompletedExceptionally())
      retry = io.ask("Retry unchanged request with SAME ID? y/N: ").equalsIgnoreCase("y");
    showMutation(checked(pending.resolve(context.user().id(), id, retry, Duration.ofSeconds(10))));
  }

  public void menu(boolean showHistory, boolean responder) throws IOException, TimeoutException {
    io.heading("HISTORY AND ACCOUNT");
    io.print(
        "  1  My account\n  2  Saved actions"
            + (showHistory ? "\n  3  History" : "")
            + (responder ? "\n  4  Assignment timeline" : "")
            + "\n  0  Back");
    switch (io.ask("Choose: ").strip()) {
      case "1" -> io.result(read("WHO_AM_I", Map.of()));
      case "2" -> pending();
      case "3" -> {
        if (showHistory) history();
      }
      case "4" -> {
        if (responder) new ResponderActions(context).assignmentTimeline();
      }
      case "0" -> {}
      default -> io.print("Choose a displayed number.");
    }
  }
}
