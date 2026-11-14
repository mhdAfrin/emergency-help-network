package lk.emergency.client.app;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import lk.emergency.client.actions.*;
import lk.emergency.client.network.*;
import lk.emergency.client.recovery.*;
import lk.emergency.client.support.*;
import lk.emergency.client.ui.*;
import lk.emergency.common.*;
import lk.emergency.common.Types.ErrorCode;

/** Application lifecycle and menu routing. Business actions live in separate role files. */
public final class ClientApplication {
  private ClientApplication() {}

  public static void main(String[] args) {
    System.setOut(new PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(new PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    ConsoleIO io = new ConsoleIO();
    io.heading("EMERGENCY HELP NETWORK");
    io.print(Validation.DISCLAIMER);
    try {
      String host = io.ask("Server host [127.0.0.1]: ").strip();
      if (host.isEmpty()) host = "127.0.0.1";
      String portText = io.ask("Server port [5050]: ").strip();
      int port = portText.isEmpty() ? 5050 : Integer.parseInt(portText);
      LiveState state = new LiveState();
      LocalJournal journal =
          new LocalJournal(java.nio.file.Path.of(".local/client"), host + ":" + port);
      try (ReconnectingClient network =
          new ReconnectingClient(
              host,
              port,
              event -> {
                if (state.accept(event)) io.event(event);
              },
              io::print)) {
        run(new ClientContext(io, network, state, journal));
      }
    } catch (EOFException ignored) {
    } catch (Exception failure) {
      io.print("Client stopped: " + failure.getMessage());
    }
  }

  private static void run(ClientContext session) throws IOException {
    var io = session.io;
    var menus = new MenuRenderer(io);
    var auth = new AuthActions(session);
    var citizen = new CitizenActions(session);
    var operator = new OperatorActions(session);
    var responder = new ResponderActions(session);
    var account = new AccountActions(session);
    while (true) {
      if (session.user() != null && !session.connection.authenticated()) {
        session.signOut();
        io.print("Connection changed. Login again; saved actions are kept.");
      }
      menus.show(session.user());
      String choice = io.ask("Choose: ").strip();
      if (choice.equals("0")) return;
      try {
        if (session.user() != null && !session.connection.authenticated()) session.signOut();
        if (session.user() == null) {
          auth.handle(choice);
          continue;
        }
        if (choice.equals("6")) {
          io.result(session.read("LOGOUT", Map.of()));
          session.signOut();
          continue;
        }
        switch (session.user().role()) {
          case CITIZEN -> {
            switch (choice) {
              case "1" -> citizen.report();
              case "2" -> citizen.listIncidents();
              case "3" -> citizen.details();
              case "4" -> account.history();
              case "5" -> account.menu(false, false);
              default -> io.print("Choose a displayed number.");
            }
          }
          case OPERATOR -> {
            switch (choice) {
              case "1" -> citizen.listIncidents();
              case "2" -> operator.assign();
              case "3" -> operator.manage();
              case "4" -> operator.listTeams();
              case "5" -> account.menu(true, false);
              default -> io.print("Choose a displayed number.");
            }
          }
          case RESPONDER -> {
            switch (choice) {
              case "1" -> responder.current();
              case "2" -> responder.decision();
              case "3" -> responder.progress();
              case "4" -> responder.teamMenu();
              case "5" -> account.menu(true, true);
              default -> io.print("Choose a displayed number.");
            }
          }
        }
      } catch (ClientContext.RemoteFailure failure) {
        io.print("Action could not finish: " + failure.getMessage());
        if (failure.code == ErrorCode.UNAUTHENTICATED) session.signOut();
      } catch (EOFException end) {
        throw end;
      } catch (IOException | TimeoutException failure) {
        io.print(failure.getMessage());
      } catch (IllegalArgumentException failure) {
        io.print(
            "Please enter a displayed number, valid reference, or one of the listed filter"
                + " values.");
      }
    }
  }
}
