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

public abstract class ActionSupport {
  protected final ClientContext context;
  protected final ConsoleIO io;
  protected final ReconnectingClient connection;
  protected final LiveState live;
  protected final LocalJournal journal;
  protected final PendingActions pending;

  protected ActionSupport(ClientContext context) {
    this.context = context;
    io = context.io;
    connection = context.connection;
    live = context.live;
    journal = context.journal;
    pending = context.pending;
  }

  protected JsonNode read(String type, Object value) throws IOException, TimeoutException {
    return context.read(type, value);
  }

  protected Envelope checked(Envelope response) {
    return context.checked(response);
  }

  protected void mutate(String type, Object value) throws IOException, TimeoutException {
    context.mutate(type, value);
  }

  protected void showMutation(Envelope response) {
    context.showMutation(response);
  }

  protected boolean confirm() throws IOException {
    return io.ask("Review the displayed values. Confirm? y/N: ").equalsIgnoreCase("y");
  }

  protected <T extends Enum<T>> T optionalEnum(Class<T> type, String prompt) throws IOException {
    String value = io.ask(prompt).strip();
    return value.isEmpty() ? null : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
  }

  protected JsonNode incident(String id) throws IOException, TimeoutException {
    return read("GET_INCIDENT", new IncidentQuery(id, null, 0, 0)).get("incident");
  }

  protected JsonNode team(String id) throws IOException, TimeoutException {
    for (JsonNode team : read("LIST_TEAMS", Map.of()))
      if (team.get("id").asText().equals(id)) return team;
    throw new IllegalArgumentException("Unknown team");
  }
}
