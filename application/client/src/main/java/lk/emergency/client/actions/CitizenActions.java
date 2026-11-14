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

public final class CitizenActions extends ActionSupport {
  public CitizenActions(ClientContext context) {
    super(context);
  }

  public void report() throws IOException, TimeoutException {
    if (pending.list(context.user().id()).stream()
            .anyMatch(t -> t.request().type().equals("CREATE_INCIDENT"))
        && !io.ask(
                "A report is unconfirmed; open Account and saved actions to check it. Start a"
                    + " different report? y/N: ")
            .equalsIgnoreCase("y")) return;
    io.print(Validation.DISCLAIMER);
    LocalJournal.Draft saved = journal.draft(context.user().id());
    if (saved != null && !io.ask("Resume your saved unsent report? y/N: ").equalsIgnoreCase("y"))
      saved = null;
    LocalJournal.Draft draft = saved == null ? new LocalJournal.Draft("", "", "") : saved;
    String category =
        draftValue("Category MEDICAL/ROAD_ACCIDENT/FIRE/CRIME/OTHER", draft.category());
    draft = new LocalJournal.Draft(category, draft.address(), draft.description());
    journal.draft(context.user().id(), draft);
    String address = draftValue("Address/landmark", draft.address());
    draft = new LocalJournal.Draft(category, address, draft.description());
    journal.draft(context.user().id(), draft);
    String description = draftValue("Description", draft.description());
    draft = new LocalJournal.Draft(category, address, description);
    journal.draft(context.user().id(), draft);
    mutate(
        "CREATE_INCIDENT",
        new IncidentInput(
            Category.valueOf(category.toUpperCase(Locale.ROOT)), address, description, null, null));
  }

  public String draftValue(String name, String saved) throws IOException {
    String value = io.ask(name + (saved.isEmpty() ? ": " : " [Enter keeps saved; - clears]: "));
    return value.equals("-") ? "" : value.isEmpty() ? saved : value;
  }

  public void listIncidents() throws IOException, TimeoutException {
    IncidentState state = null;
    Priority priority = null;
    if (context.user().role() == Role.OPERATOR) {
      state = optionalEnum(IncidentState.class, "Status REPORTED/ACTIVE/CLOSED/REJECTED (Enter=all open): ");
      priority = optionalEnum(Priority.class, "Priority LOW/MEDIUM/HIGH (Enter=all): ");
    }
    for (int offset = 0; ; offset += 5) {
      JsonNode page = read("LIST_INCIDENTS", new IncidentFilter(state, priority, offset, 5));
      io.result(page);
      if (page.size() < 5 || !io.ask("N for next page, Enter to return: ").equalsIgnoreCase("n"))
        break;
    }
  }

  public void details() throws IOException, TimeoutException {
    String id = io.ask("Incident ID: ").strip();
    int a = 0, t = 0;
    while (true) {
      JsonNode response = read("GET_INCIDENT", new IncidentQuery(id, null, a, t));
      io.result(response.get("incident"));
      io.print("Service progress:");
      io.result(response.get("assignments"));
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
}
