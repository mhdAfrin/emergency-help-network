package lk.emergency.client.ui;

import lk.emergency.common.Dtos.UserView;

public final class MenuRenderer {
  private final ConsoleIO io;

  public MenuRenderer(ConsoleIO io) {
    this.io = io;
  }

  public void show(UserView user) {
    if (user == null) {
      io.heading("EMERGENCY HELP NETWORK | SIGN IN");
      io.print("  1  Create citizen account\n  2  Login\n  0  Exit");
      return;
    }
    io.heading(user.displayName() + " [" + user.role() + "]");
    switch (user.role()) {
      case CITIZEN ->
          io.print(
              "  1  Report an incident\n"
                  + "  2  My open reports\n"
                  + "  3  View a report and its progress\n"
                  + "  4  My history\n"
                  + "  5  Account and saved actions");
      case OPERATOR ->
          io.print(
              "  1  Open reports\n"
                  + "  2  Assign a team\n"
                  + "  3  Manage an incident\n"
                  + "  4  Teams and availability\n"
                  + "  5  History and account");
      case RESPONDER ->
          io.print(
              "  1  My current assignment\n"
                  + "  2  Accept or decline an offer\n"
                  + "  3  Update my progress\n"
                  + "  4  My team and availability\n"
                  + "  5  History and saved actions");
    }
    io.print("  6  Logout\n  0  Exit");
  }
}
