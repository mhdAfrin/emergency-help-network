package lk.emergency.client;

import java.io.*;
import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class ClientMain {
  public static void main(String[] args) {
    System.setOut(
        new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(
        new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
    ConsoleIO console = new ConsoleIO();
    console.print(Validation.DISCLAIMER);
    try {
      String host = console.ask("Server host [127.0.0.1]: ").strip();
      if (host.isEmpty()) host = "127.0.0.1";
      String rawPort = console.ask("Server port [5050]: ").strip();
      int port = rawPort.isEmpty() ? 5050 : Integer.parseInt(rawPort);
      try (ConnectionManager connection = new ConnectionManager(host, port, console::event)) {
        UserView user = null;
        while (!connection.isClosed()) {
          if (user == null) console.print("\n1 Register citizen | 2 Login | 3 Ping | 0 Exit");
          else
            console.print(
                "\n"
                    + user.displayName()
                    + " ["
                    + user.role()
                    + "] — 1 My identity | 2 Ping"
                    + (user.role() == Types.Role.CITIZEN
                        ? ""
                        : " | 3 "
                            + (user.role() == Types.Role.RESPONDER
                                ? "My team"
                                : "List teams | 4 Private test event"))
                    + " | 9 Logout | 0 Exit");
          String action = console.ask("> ").strip();
          if (action.equals("0")) break;
          try {
            Envelope result;
            if (user == null) {
              result =
                  switch (action) {
                    case "1" ->
                        connection.call(
                            "REGISTER_CITIZEN",
                            new Registration(
                                console.ask("Username: "),
                                console.ask("Display name: "),
                                console.password(),
                                console.ask("Synthetic contact (optional): ")));
                    case "2" ->
                        connection.call(
                            "LOGIN", new Login(console.ask("Username: "), console.password()));
                    case "3" -> connection.call("PING", Map.of());
                    default -> null;
                  };
              if (result != null
                  && result.type().equals("LOGIN")
                  && Boolean.TRUE.equals(result.ok()))
                user = Json.MAPPER.treeToValue(result.payload(), UserView.class);
            } else {
              String type =
                  switch (action) {
                    case "1" -> "WHO_AM_I";
                    case "2" -> "PING";
                    case "3" -> user.role() == Types.Role.CITIZEN ? null : "LIST_TEAMS";
                    case "4" -> user.role() == Types.Role.OPERATOR ? "TEST_EVENT" : null;
                    case "9" -> "LOGOUT";
                    default -> null;
                  };
              result = type == null ? null : connection.call(type, Map.of());
              if (result != null
                  && result.type().equals("LOGOUT")
                  && Boolean.TRUE.equals(result.ok())) user = null;
            }
            if (result == null) console.print("Choose one of the displayed actions.");
            else if (Boolean.TRUE.equals(result.ok()))
              console.print(
                  Json.MAPPER
                      .writerWithDefaultPrettyPrinter()
                      .writeValueAsString(result.payload()));
            else {
              console.print(result.error().code() + ": " + result.error().message());
              if (result.error().code() == Types.ErrorCode.UNAUTHENTICATED) user = null;
            }
          } catch (java.util.concurrent.TimeoutException e) {
            console.print(e.getMessage());
          }
        }
        if (connection.isClosed()) console.print("Disconnected. Restart client and log in again.");
      }
    } catch (EOFException e) {
      /* Exit on end of console input. */
    } catch (Exception e) {
      console.print("Client stopped: " + e.getMessage());
    }
  }
}
