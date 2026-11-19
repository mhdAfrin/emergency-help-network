package lk.emergency.client.desktop.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import javax.swing.*;
import lk.emergency.client.desktop.ClientDesktop;
import lk.emergency.common.Dtos.UserView;
import lk.emergency.common.Types.Role;
import lk.emergency.common.desktop.DesktopTheme;

/** Four simple work areas per role. Selection-based actions avoid manual reference entry. */
public final class ClientDashboard extends JPanel {
  public final RecordTable open, history, teams, saved;
  public final ReportPanel report;
  public final JTabbedPane tabs = new JTabbedPane();
  public final JLabel summary = DesktopTheme.label("Loading your current information...", 14, true);
  public final JTextArea updates = new JTextArea(3, 45);
  public final Role role;
  private final JButton accept, decline, progress;

  public ClientDashboard(ClientDesktop app, UserView user) {
    super(new BorderLayout(0, 12));
    role = user.role();
    setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
    var heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    var identity = DesktopTheme.stack();
    identity.add(DesktopTheme.label("Welcome, " + user.displayName(), 25, true));
    JLabel detail =
        DesktopTheme.label(
            RecordTable.words(role.name())
                + "  |  Account #"
                + user.id()
                + "  |  "
                + app.endpoint(),
            13,
            false);
    detail.setForeground(DesktopTheme.MUTED);
    identity.add(detail);
    heading.add(identity, BorderLayout.CENTER);
    heading.add(
        DesktopTheme.row(
            DesktopTheme.button("Account", false, app::account),
            DesktopTheme.button("Refresh", false, app::refresh),
            DesktopTheme.button("Sign out", false, app::logout)),
        BorderLayout.EAST);
    JPanel top = new JPanel(new BorderLayout(0, 12));
    top.setOpaque(false);
    top.add(heading, BorderLayout.NORTH);
    JPanel summaryCard = new JPanel(new BorderLayout());
    summaryCard.setBackground(Color.WHITE);
    summaryCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DesktopTheme.LINE),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)));
    summaryCard.add(summary);
    top.add(summaryCard, BorderLayout.SOUTH);
    add(top, BorderLayout.NORTH);
    open =
        new RecordTable(
            role == Role.RESPONDER ? RecordTable.Kind.ASSIGNMENT : RecordTable.Kind.INCIDENT);
    history =
        new RecordTable(
            role == Role.RESPONDER ? RecordTable.Kind.ASSIGNMENT : RecordTable.Kind.INCIDENT);
    teams = new RecordTable(RecordTable.Kind.TEAM);
    saved = new RecordTable(RecordTable.Kind.SAVED);
    report = role == Role.CITIZEN ? new ReportPanel(app::submitReport, app::saveDraft) : null;
    if (report != null) {
      report.onChange(app::scheduleDraft);
      tabs.addTab("  Report incident  ", report);
    }
    JPanel active =
        DesktopTheme.card(
            role == Role.RESPONDER
                ? "My assignments"
                : role == Role.CITIZEN ? "My open reports" : "Open reports",
            role == Role.RESPONDER
                ? "Select your assignment, then use the next allowed action."
                : "Select a report to view details and progress.");
    active.add(open, BorderLayout.CENTER);
    accept = DesktopTheme.button("Accept offer", true, () -> app.respond(true));
    decline = DesktopTheme.button("Decline", false, () -> app.respond(false));
    progress = DesktopTheme.button("Next progress", true, app::progress);
    if (role == Role.OPERATOR)
      active.add(
          DesktopTheme.row(
              DesktopTheme.button("View details", false, () -> app.details(open, false)),
              DesktopTheme.button("Assign team", true, app::assign),
              DesktopTheme.button("Priority", false, app::priority),
              DesktopTheme.button("Close report", false, app::closeIncident),
              DesktopTheme.button("More actions", false, app::moreActions)),
          BorderLayout.SOUTH);
    else if (role == Role.RESPONDER)
      active.add(
          DesktopTheme.row(
              DesktopTheme.button("View details", false, () -> app.details(open, true)),
              accept,
              decline,
              progress),
          BorderLayout.SOUTH);
    else
      active.add(
          DesktopTheme.row(
              DesktopTheme.button("View progress", true, () -> app.details(open, false))),
          BorderLayout.SOUTH);
    tabs.addTab(role == Role.RESPONDER ? "  My assignments  " : "  Open reports  ", active);
    if (role != Role.CITIZEN) {
      JPanel teamCard =
          DesktopTheme.card(
              role == Role.RESPONDER ? "My team" : "Response teams",
              "A team must be online and available before the operator can assign it.");
      teamCard.add(teams, BorderLayout.CENTER);
      if (role == Role.RESPONDER)
        teamCard.add(
            DesktopTheme.row(DesktopTheme.button("Change availability", false, app::availability)),
            BorderLayout.SOUTH);
      tabs.addTab(role == Role.RESPONDER ? "  My team  " : "  Teams  ", teamCard);
    }
    JPanel old =
        DesktopTheme.card(
            "History", "Completed, rejected and other terminal records for your authorized role.");
    old.add(history, BorderLayout.CENTER);
    old.add(
        DesktopTheme.row(
            DesktopTheme.button(
                "View details", false, () -> app.details(history, role == Role.RESPONDER))),
        BorderLayout.SOUTH);
    tabs.addTab("  History  ", old);
    JPanel pending =
        DesktopTheme.card(
            "Saved actions",
            "If a reply was lost, reconcile the original action here instead of creating a"
                + " duplicate.");
    pending.add(saved, BorderLayout.CENTER);
    pending.add(
        DesktopTheme.row(DesktopTheme.button("Check / retry selected", true, app::retry)),
        BorderLayout.SOUTH);
    tabs.addTab("  Saved actions  ", pending);
    tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
    add(tabs, BorderLayout.CENTER);
    updates.setName("Live updates");
    updates.setEditable(false);
    updates.setLineWrap(true);
    updates.setWrapStyleWord(true);
    updates.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    updates.setBackground(new Color(233, 241, 250));
    updates.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    JPanel bottom = new JPanel(new BorderLayout(0, 5));
    bottom.setOpaque(false);
    bottom.add(
        DesktopTheme.label("LIVE UPDATES  /  You can keep working while updates arrive", 11, true),
        BorderLayout.NORTH);
    bottom.add(new JScrollPane(updates), BorderLayout.CENTER);
    add(bottom, BorderLayout.SOUTH);
    open.table.getSelectionModel().addListSelectionListener(e -> updateActions());
    updateActions();
  }

  public void updateActions() {
    JsonNode row = open.selected();
    String state = row == null ? "" : row.path("assignment").path("state").asText();
    accept.setEnabled(state.equals("OFFERED"));
    decline.setEnabled(state.equals("OFFERED"));
    var next = lk.emergency.client.desktop.DesktopSession.nextState(state);
    progress.setEnabled(next != null);
    progress.setText(
        next == null
            ? "Next progress"
            : "Mark " + RecordTable.words(next.name()).toLowerCase(java.util.Locale.ROOT));
  }

  public void event(String text) {
    String existing = updates.getText();
    if (existing.length() > 12000) existing = existing.substring(existing.length() - 8000);
    updates.setText(existing + text + "\n");
    updates.setCaretPosition(updates.getDocument().getLength());
  }
}
