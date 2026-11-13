
package lk.emergency.client;

import java.io.*;
import java.util.*;

import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class ClientMain {

  public static void main(String[] args) {
    System.setOut(
      new PrintStream(System.out, true,
        java.nio.charset.StandardCharsets.UTF_8));
    System.setErr(
      new PrintStream(System.err, true,
        java.nio.charset.StandardCharsets.UTF_8));

    ConsoleIO console = new ConsoleIO();
    console.print(Validation.DISCLAIMER);

    try {
      String host = console.ask("Server host [127.0.0.1]: ").strip();
      if (host.isEmpty()) host = "127.0.0.1";

      String rawPort = console.ask("Server port [5050]: ").strip();
      int port = rawPort.isEmpty()
        ? 5050 : Integer.parseInt(rawPort);

      try (ConnectionManager connection =
             new ConnectionManager(host, port, console::event)) {

        UserView user = null;

        while (!connection.isClosed()) {

          if (user == null) {
            console.print(
              "\n1 Register citizen | 2 Login | 3 Ping | 0 Exit");
          } else {
            String menu = "\n" + user.displayName()
              + " [" + user.role() + "] - "
              + "1 My identity | 2 Ping";

            if (user.role() == Types.Role.CITIZEN) {
              menu += " | 3 Report incident"
                + " | 4 My incidents"
                + " | 5 View incident";

            } else if (user.role() == Types.Role.RESPONDER) {
              menu += " | 3 My team";

            } else if (user.role() == Types.Role.OPERATOR) {
              menu += " | 3 List teams"
                + " | 4 Private test event"
                + " | 5 List incidents"
                + " | 6 View incident"
                + " | 7 Set incident priority"
                + " | 8 Assign emergency team"
                + " | 10 Cancel emergency team assignment"
                + " | 11 Reject incident"
                + " | 12 Close incident";
            }

            console.print(menu + " | 9 Logout | 0 Exit");
          }

          String action = console.ask("> ").strip();
          if (action.equals("0")) break;

          try {
            Envelope result;

            if (user == null) {
              result = switch (action) {

                case "1" -> connection.call(
                  "REGISTER_CITIZEN",
                  new Registration(
                    console.ask("Username: "),
                    console.ask("Display name: "),
                    console.password(),
                    console.ask("Synthetic contact (optional): ")));

                case "2" -> connection.call(
                  "LOGIN",
                  new Login(
                    console.ask("Username: "),
                    console.password()));

                case "3" -> connection.call("PING", Map.of());

                default -> null;
              };

              if (result != null
                && result.type().equals("LOGIN")
                && Boolean.TRUE.equals(result.ok())) {
                user = Json.MAPPER.treeToValue(
                  result.payload(), UserView.class);
              }

            } else {

              if (action.equals("3")
                && user.role() == Types.Role.CITIZEN) {

                console.print("\nReport a new incident");

                Types.Category category;
                try {
                  category = Types.Category.valueOf(
                    console.ask("Category: ")
                      .strip()
                      .toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                  console.print(
                    "Invalid category. Please try again.");
                  continue;
                }

                String address = console.ask("Address: ");
                String description =
                  console.ask("Description: ");

                result = connection.call(
                  "CREATE_INCIDENT",
                  new IncidentInput(
                    category,
                    address,
                    description,
                    null,
                    null));

              } else if (
                (action.equals("5")
                  && user.role() == Types.Role.CITIZEN)
                  || (action.equals("6")
                  && user.role() == Types.Role.OPERATOR)) {

                String incidentId =
                  console.ask("Incident ID: ").strip();

                if (incidentId.isEmpty()) {
                  console.print("Incident ID is required.");
                  continue;
                }

                result = connection.call(
                  "GET_INCIDENT",
                  new RevisionInput(incidentId, 0L));

              } else if (
                action.equals("7")
                  && user.role() == Types.Role.OPERATOR) {

                String incidentId =
                  console.ask("Incident ID: ").strip();

                if (incidentId.isEmpty()) {
                  console.print("Incident ID is required.");
                  continue;
                }

                Types.Priority priority;
                try {
                  priority = Types.Priority.valueOf(
                    console.ask("Priority (LOW/MEDIUM/HIGH): ")
                      .strip()
                      .toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                  console.print("Invalid priority.");
                  continue;
                }

                long expectedRevision;
                try {
                  expectedRevision = Long.parseLong(
                    console.ask("Current incident revision: ").strip());

                  if (expectedRevision < 0) {
                    console.print("Revision cannot be negative.");
                    continue;
                  }
                } catch (NumberFormatException e) {
                  console.print("Invalid revision.");
                  continue;
                }

                result = connection.call(
                  "SET_PRIORITY",
                  new PriorityUpdateInput(
                    incidentId,
                    priority,
                    expectedRevision));

              } else if (
                action.equals("8")
                  && user.role() == Types.Role.OPERATOR) {

                console.print("\nAssign an emergency response team");
                console.print(
                  "Use option 3 to see available team IDs.");
                console.print(
                  "Use option 6 to check the incident revision.");

                String incidentId =
                  console.ask("Incident ID: ").strip();

                if (incidentId.isEmpty()) {
                  console.print("Incident ID is required.");
                  continue;
                }

                String teamId =
                  console.ask("Team ID: ").strip();

                if (teamId.isEmpty()) {
                  console.print("Team ID is required.");
                  continue;
                }

                long expectedRevision;
                try {
                  expectedRevision = Long.parseLong(
                    console.ask("Current incident revision: ").strip());

                  if (expectedRevision < 0) {
                    console.print("Revision cannot be negative.");
                    continue;
                  }
                } catch (NumberFormatException e) {
                  console.print("Invalid revision.");
                  continue;
                }

                result = connection.call(
                  "ASSIGN_TEAM",
                  new AssignmentInput(
                    incidentId,
                    teamId,
                    expectedRevision));

              } else if (
                action.equals("10")
                  && user.role() == Types.Role.OPERATOR) {

                console.print("\nCancel emergency team assignment");
                console.print(
                  "Use the assignment ID returned when the team was assigned.");

                String assignmentId =
                  console.ask("Assignment ID: ").strip();

                if (assignmentId.isEmpty()) {
                  console.print("Assignment ID is required.");
                  continue;
                }

                String reason =
                  console.ask("Cancellation reason: ").strip();

                if (reason.isEmpty() || reason.length() > 500) {
                  console.print(
                    "Reason must contain 1-500 characters.");
                  continue;
                }

                long expectedRevision;
                try {
                  expectedRevision = Long.parseLong(
                    console.ask("Current assignment revision: ").strip());

                  if (expectedRevision < 0) {
                    console.print("Revision cannot be negative.");
                    continue;
                  }
                } catch (NumberFormatException e) {
                  console.print("Invalid revision.");
                  continue;
                }

                result = connection.call(
                  "CANCEL_ASSIGNMENT",
                  new AssignmentCancellationInput(
                    assignmentId,
                    reason,
                    expectedRevision));

              } else if (
                action.equals("11")
                  && user.role() == Types.Role.OPERATOR) {

                console.print("\nReject a reported incident");
                console.print(
                  "Use option 6 to check the incident state and revision.");

                String incidentId =
                  console.ask("Incident ID: ").strip();

                if (incidentId.isEmpty()) {
                  console.print("Incident ID is required.");
                  continue;
                }

                String reason =
                  console.ask("Rejection reason: ").strip();

                if (reason.isEmpty() || reason.length() > 500) {
                  console.print(
                    "Reason must contain 1-500 characters.");
                  continue;
                }

                long expectedRevision;
                try {
                  expectedRevision = Long.parseLong(
                    console.ask("Current incident revision: ").strip());

                  if (expectedRevision < 0) {
                    console.print("Revision cannot be negative.");
                    continue;
                  }
                } catch (NumberFormatException e) {
                  console.print("Invalid revision.");
                  continue;
                }

                result = connection.call(
                  "REJECT_INCIDENT",
                  new IncidentRejectionInput(
                    incidentId,
                    reason,
                    expectedRevision));

              } else if (
                action.equals("12")
                  && user.role() == Types.Role.OPERATOR) {

                console.print("\nClose a resolved incident");
                console.print(
                  "Use option 6 to check the incident state and revision.");

                String incidentId =
                  console.ask("Incident ID: ").strip();

                if (incidentId.isEmpty()) {
                  console.print("Incident ID is required.");
                  continue;
                }

                String reason =
                  console.ask("Resolution reason: ").strip();

                if (reason.isEmpty() || reason.length() > 500) {
                  console.print(
                    "Reason must contain 1-500 characters.");
                  continue;
                }

                long expectedRevision;
                try {
                  expectedRevision = Long.parseLong(
                    console.ask("Current incident revision: ").strip());

                  if (expectedRevision < 0) {
                    console.print("Revision cannot be negative.");
                    continue;
                  }
                } catch (NumberFormatException e) {
                  console.print("Invalid revision.");
                  continue;
                }

                result = connection.call(
                  "CLOSE_INCIDENT",
                  new IncidentClosureInput(
                    incidentId,
                    reason,
                    expectedRevision));

              } else {
                String type;

                if (user.role() == Types.Role.CITIZEN
                  && action.equals("4")) {
                  type = "LIST_INCIDENTS";

                } else if (user.role() == Types.Role.OPERATOR
                  && action.equals("5")) {
                  type = "LIST_INCIDENTS";

                } else {
                  type = switch (action) {
                    case "1" -> "WHO_AM_I";
                    case "2" -> "PING";

                    case "3" -> user.role()
                      == Types.Role.CITIZEN
                      ? null : "LIST_TEAMS";

                    case "4" -> user.role()
                      == Types.Role.OPERATOR
                      ? "TEST_EVENT" : null;

                    case "9" -> "LOGOUT";

                    default -> null;
                  };
                }

                result = type == null
                  ? null
                  : connection.call(type, Map.of());
              }

              if (result != null
                && result.type().equals("LOGOUT")
                && Boolean.TRUE.equals(result.ok())) {
                user = null;
              }
            }

            if (result == null) {
              console.print(
                "Choose one of the displayed actions.");

            } else if (Boolean.TRUE.equals(result.ok())) {
              console.print(
                Json.MAPPER.writerWithDefaultPrettyPrinter()
                  .writeValueAsString(result.payload()));

            } else {
              console.print(
                result.error().code() + ": "
                  + result.error().message());

              if (result.error().code()
                == Types.ErrorCode.UNAUTHENTICATED) {
                user = null;
              }
            }

          } catch (java.util.concurrent.TimeoutException e) {
            console.print(e.getMessage());
          }
        }

        if (connection.isClosed()) {
          console.print(
            "Disconnected. Restart client and log in again.");
        }
      }

    } catch (EOFException e) {
      // Exit when console input ends.
    } catch (Exception e) {
      console.print("Client stopped: " + e.getMessage());
    }
  }
}
