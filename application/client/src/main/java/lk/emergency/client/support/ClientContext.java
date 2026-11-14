package lk.emergency.client.support;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.client.ui.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class ClientContext {
  public final ConsoleIO io;
  public final ReconnectingClient connection;
  public final LiveState live;
  public final LocalJournal journal;
  public final PendingActions pending;
  private UserView user;

  public ClientContext(
      ConsoleIO io, ReconnectingClient connection, LiveState live, LocalJournal journal) {
    this.io = io;
    this.connection = connection;
    this.live = live;
    this.journal = journal;
    this.pending = new PendingActions(connection, journal);
  }

  public UserView user() {
    return user;
  }

  public void signIn(UserView value) {
    user = value;
  }

  public void signOut() {
    user = null;
  }

  public JsonNode read(String type, Object payload) throws IOException, TimeoutException {
    return checked(connection.call(type, payload)).payload();
  }

  public Envelope checked(Envelope response) {
    if (!Boolean.TRUE.equals(response.ok()))
      throw new RemoteFailure(response.error().code(), response.error().message());
    live.response(response);
    return response;
  }

  public void mutate(String type, Object payload) throws IOException, TimeoutException {
    showMutation(checked(pending.submit(user.id(), type, payload)));
  }

  public void showMutation(Envelope response) {
    io.result(response.payload());
    if (response.type().equals("CREATE_INCIDENT"))
      io.print("Report saved. Incident reference: " + response.payload().path("id").asText());
  }

  public static final class RemoteFailure extends RuntimeException {
    public final ErrorCode code;

    public RemoteFailure(ErrorCode code, String message) {
      super(message);
      this.code = code;
    }
  }
}
