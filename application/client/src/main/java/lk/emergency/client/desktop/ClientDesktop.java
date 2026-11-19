package lk.emergency.client.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;
import lk.emergency.client.desktop.view.*;
import lk.emergency.client.network.ClientOptions;
import lk.emergency.client.recovery.LocalJournal.Draft;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.UserView;
import lk.emergency.common.Types.*;
import lk.emergency.common.desktop.DesktopTheme;

/** Desktop lifecycle and button handlers; business/protocol code stays in DesktopSession. */
public final class ClientDesktop extends JFrame {
  @FunctionalInterface
  public interface Work<T> {
    T run() throws Exception;
  }

  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "desktop-client-work"));
  private DesktopSession session;
  private SignInPanel signIn;
  private ClientDashboard dashboard;
  private final JLabel status = DesktopTheme.label("Ready to connect", 12, false);
  private final JPanel body = new JPanel(new BorderLayout());
  private boolean busy, dirty, closing;
  private final javax.swing.Timer updates, drafts;
  private final Path settings = Path.of(".local/ui/endpoint.properties");

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          DesktopTheme.install();
          new ClientDesktop().setVisible(true);
        });
  }

  public ClientDesktop() {
    super("Emergency Help Network | Client");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(980, 710));
    setSize(1130, 800);
    setLocationByPlatform(true);
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(DesktopTheme.NAVY);
    header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
    JLabel brand = DesktopTheme.label("EHN   Emergency Help Network", 19, true);
    brand.setForeground(Color.WHITE);
    header.add(brand, BorderLayout.WEST);
    JLabel badge = DesktopTheme.label("DESKTOP  /  WI-FI READY", 11, true);
    badge.setForeground(new Color(130, 218, 210));
    header.add(badge, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    status.setBorder(BorderFactory.createEmptyBorder(8, 22, 9, 22));
    add(status, BorderLayout.SOUTH);
    JPanel glass = new JPanel(new GridBagLayout());
    glass.setOpaque(false);
    glass.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    glass.addMouseListener(new MouseAdapter() {});
    glass.addKeyListener(new KeyAdapter() {});
    setGlassPane(glass);
    drafts = new javax.swing.Timer(700, e -> saveDraftSilently());
    drafts.setRepeats(false);
    updates = new javax.swing.Timer(650, e -> tick());
    updates.start();
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            closeWindow();
          }
        });
    showSignIn("Start the server, then enter its Wi-Fi IP.");
  }

  private void showSignIn(String message) {
    for (Window owned : getOwnedWindows()) owned.dispose();
    dashboard = null;
    drafts.stop();
    signIn = new SignInPanel(this::connect, this::login, this::register);
    try {
      if (Files.exists(settings)) {
        Properties p = new Properties();
        try (var in = Files.newInputStream(settings)) {
          p.load(in);
        }
        signIn.host.setText(p.getProperty("host", "127.0.0.1"));
        signIn.port.setText(p.getProperty("port", "5050"));
      }
    } catch (Exception ignored) {
    }
    signIn.connected(session != null && session.connected(), message);
    body.removeAll();
    body.add(signIn);
    body.revalidate();
    body.repaint();
    getRootPane().setDefaultButton(signIn.login);
  }

  public String endpoint() {
    return session == null ? "Not connected" : session.endpoint;
  }

  public <T> void task(String title, Work<T> work, Consumer<T> success) {
    if (busy || closing) return;
    busy = true;
    status.setText(title + "...");
    getGlassPane().setVisible(true);
    worker.submit(
        () -> {
          T result = null;
          Exception error = null;
          try {
            result = work.run();
          } catch (Exception failure) {
            error = failure;
          }
          final T value = result;
          final Exception failure = error;
          SwingUtilities.invokeLater(
              () -> {
                busy = false;
                getGlassPane().setVisible(false);
                if (closing) return;
                if (failure != null) {
                  status.setText("Action needs attention");
                  DesktopTheme.error(this, failure);
                } else {
                  status.setText(title + " complete");
                  success.accept(value);
                }
              });
        });
  }

  private void connect() {
    String host = signIn.host.getText().strip();
    String portText = signIn.port.getText().strip();
    task(
        "Connecting",
        () -> {
          int port = ClientOptions.parsePort(portText);
          ClientOptions.validateHost(host);
          DesktopSession replacement =
              new DesktopSession(
                  host, port, Path.of(".local/client-ui"), this::networkStatus, this::event);
          DesktopSession previous = session;
          session = replacement;
          if (previous != null) previous.close();
          Properties p = new Properties();
          p.setProperty("host", host);
          p.setProperty("port", Integer.toString(port));
          Files.createDirectories(settings.getParent());
          try (var out = Files.newOutputStream(settings)) {
            p.store(out, "Server endpoint only; credentials are not saved.");
          }
          return replacement;
        },
        value -> {
          signIn.connected(
              true, "Connected to " + value.endpoint + ". Enter your username and password.");
          signIn.username.requestFocusInWindow();
        });
  }

  private void login() {
    if (session == null || !session.connected()) {
      DesktopTheme.error(this, new Exception("Connect to the server first."));
      return;
    }
    String username = signIn.username.getText();
    char[] chars = signIn.password.getPassword();
    String password = new String(chars);
    Arrays.fill(chars, '\0');
    signIn.password.setText("");
    task(
        "Signing in",
        () -> session.login(username, password),
        user -> {
          setTitle("Emergency Help Network | " + user.displayName() + " [" + user.role() + "]");
          dashboard = new ClientDashboard(this, user);
          body.removeAll();
          body.add(dashboard);
          body.revalidate();
          body.repaint();
          getRootPane().setDefaultButton(null);
          dashboard.event("Signed in. Live updates appear here automatically.");
          dirty = true;
          if (dashboard.report != null)
            worker.submit(
                () -> {
                  try {
                    var draft = session.draft();
                    SwingUtilities.invokeLater(
                        () -> {
                          if (dashboard != null && dashboard.report != null && draft != null) {
                            dashboard.report.load(draft);
                            dashboard.report.feedback.setText(
                                "Your saved draft has been restored.");
                          }
                        });
                  } catch (Exception ignored) {
                  }
                });
        });
  }

  private void register() {
    if (session == null || !session.connected()) {
      DesktopTheme.error(
          this, new Exception("Connect to the server first, then create a citizen account."));
      return;
    }
    JTextField user = new JTextField(), name = new JTextField(), contact = new JTextField();
    JPasswordField password = new JPasswordField();
    JPanel form = DesktopTheme.form();
    DesktopTheme.field(form, 0, "Username", user);
    DesktopTheme.field(form, 1, "Display name", name);
    DesktopTheme.field(form, 2, "Password", password);
    DesktopTheme.field(form, 3, "Contact number (optional / fictional)", contact);
    if (JOptionPane.showConfirmDialog(
            this,
            form,
            "Create a citizen account",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    String chosen = user.getText(), display = name.getText(), number = contact.getText();
    char[] chars = password.getPassword();
    String secret = new String(chars);
    Arrays.fill(chars, '\0');
    password.setText("");
    task(
        "Creating citizen account",
        () -> session.register(chosen, display, secret, number),
        row -> {
          signIn.username.setText(chosen);
          signIn.connected(true, "Citizen account created. Sign in with your new password.");
        });
  }

  private void networkStatus(String text) {
    SwingUtilities.invokeLater(
        () -> {
          if (!closing) {
            status.setText(text);
            if (dashboard != null) dashboard.event(text);
          }
        });
  }

  private void event(Envelope event) {
    SwingUtilities.invokeLater(
        () -> {
          if (dashboard != null) {
            dashboard.event(
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + "  "
                    + RecordTable.words(event.type()));
            dirty = true;
          }
        });
  }

  private void tick() {
    if (closing || busy) return;
    if (dashboard != null && !session.authenticated()) {
      session.forgetLogin();
      setTitle("Emergency Help Network | Client");
      showSignIn(
          "Connection changed or this account signed in elsewhere. Wait for connection, then sign"
              + " in again.");
      return;
    }
    if (signIn != null && dashboard == null)
      signIn.login.setEnabled(session != null && session.connected());
    if (dirty && dashboard != null) refresh();
  }

  public void refresh() {
    if (dashboard == null || busy) return;
    ClientDashboard view = dashboard;
    dirty = false;
    task(
        "Updating dashboard",
        () -> {
          var active =
              view.role == Role.RESPONDER
                  ? session.assignments(false)
                  : session.incidents(false, "");
          var history =
              view.role == Role.RESPONDER ? session.assignments(true) : session.incidents(true, "");
          var teams = view.role == Role.CITIZEN ? Json.MAPPER.createArrayNode() : session.teams();
          var saved = Json.MAPPER.createArrayNode();
          for (var ticket : session.saved())
            saved.add(
                Json.MAPPER
                    .createObjectNode()
                    .put("id", ticket.request().requestId())
                    .put("type", ticket.request().type()));
          return List.of(active, history, teams, saved);
        },
        rows -> {
          if (dashboard != view) return;
          view.open.data(rows.get(0));
          view.history.data(rows.get(1));
          view.teams.data(rows.get(2));
          view.saved.data(rows.get(3));
          view.updateActions();
          view.summary.setText(
              view.open.sizeOfData()
                  + " open "
                  + (view.role == Role.RESPONDER ? "assignment(s)" : "report(s)")
                  + "     |     "
                  + view.history.sizeOfData()
                  + " history item(s)     |     "
                  + view.saved.sizeOfData()
                  + " saved action(s)     |     Connected");
        });
  }

  private JsonNode selected(RecordTable table) {
    JsonNode row = table.selected();
    if (row == null)
      JOptionPane.showMessageDialog(
          this,
          "Select a row in the table first.",
          "Select an item",
          JOptionPane.INFORMATION_MESSAGE);
    return row;
  }

  private boolean confirm(String text) {
    return JOptionPane.showConfirmDialog(
            this,
            DesktopTheme.wrap(text),
            "Confirm action",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  private String note(String title, boolean required) {
    JTextArea text = new JTextArea(4, 35);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    if (JOptionPane.showConfirmDialog(
            this,
            new JScrollPane(text),
            title + (required ? " (required)" : " (optional)"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return null;
    String result = text.getText().strip();
    if (required && result.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please enter a reason.");
      return null;
    }
    return result;
  }

  private void change(String title, Work<JsonNode> work) {
    task(
        title,
        work,
        result -> {
          dirty = true;
          if (dashboard != null) dashboard.event(title + " confirmed.");
        });
  }

  public void scheduleDraft() {
    drafts.restart();
  }

  private void saveDraftSilently() {
    if (dashboard == null || dashboard.report == null || !session.authenticated()) return;
    var draft = dashboard.report.value();
    var captured = session;
    worker.submit(
        () -> {
          try {
            captured.saveDraft(draft);
          } catch (Exception error) {
            SwingUtilities.invokeLater(
                () -> status.setText("Draft was not saved. Use Save draft before closing."));
          }
        });
  }

  public void saveDraft() {
    if (dashboard == null || dashboard.report == null) return;
    Draft value = dashboard.report.value();
    task(
        "Saving draft",
        () -> {
          session.saveDraft(value);
          return true;
        },
        ok ->
            dashboard.report.feedback.setText(
                "Draft saved on this laptop. You can continue later."));
  }

  public void submitReport() {
    Draft value = dashboard.report.value();
    if (value.address().isBlank() || value.description().isBlank()) {
      JOptionPane.showMessageDialog(this, "Enter an address and a short description first.");
      return;
    }
    if (!confirm("Submit this " + RecordTable.words(value.category()) + " report to the operator?"))
      return;
    drafts.stop();
    task(
        "Submitting report",
        () ->
            session.report(
                Category.valueOf(value.category()), value.address(), value.description()),
        row -> {
          dashboard.report.load(null);
          dashboard.report.feedback.setText("Report #" + row.path("id").asText() + " submitted.");
          dashboard.tabs.setSelectedIndex(1);
          dashboard.event("Report #" + row.path("id").asText() + " submitted to the operator.");
          dirty = true;
        });
  }

  public void details(RecordTable table, boolean assignment) {
    var row = selected(table);
    if (row == null) return;
    String id = (assignment ? row.path("assignment") : row).path("id").asText();
    task(
        "Loading details",
        () -> assignment ? session.assignmentDetails(id) : session.incidentDetails(id),
        data -> DetailsDialog.show(this, data, assignment));
  }

  public void assign() {
    var row = selected(dashboard.open);
    if (row == null) return;
    String id = row.path("id").asText();
    task(
        "Loading available teams",
        session::teams,
        data -> {
          List<JsonNode> choices = new ArrayList<>();
          for (var team : data)
            if (team.path("presence").asText().equals("ONLINE")
                && team.path("availability").asText().equals("AVAILABLE")) choices.add(team);
          if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "No online, available team. Sign in a responder or check its availability.");
            return;
          }
          String[] labels =
              choices.stream()
                  .map(
                      t ->
                          "#"
                              + t.path("id").asText()
                              + "  "
                              + RecordTable.words(t.path("serviceType").asText())
                              + "  -  "
                              + t.path("name").asText())
                  .toArray(String[]::new);
          Object picked =
              JOptionPane.showInputDialog(
                  this,
                  "Choose a team for report #" + id,
                  "Assign team",
                  JOptionPane.PLAIN_MESSAGE,
                  null,
                  labels,
                  labels[0]);
          if (picked == null) return;
          int index = Arrays.asList(labels).indexOf(picked);
          String teamId = choices.get(index).path("id").asText();
          if (confirm("Assign " + picked + " to report #" + id + "?"))
            change("Team assignment", () -> session.assign(id, teamId));
        });
  }

  public void priority() {
    var row = selected(dashboard.open);
    if (row == null) return;
    String id = row.path("id").asText();
    Priority picked =
        (Priority)
            JOptionPane.showInputDialog(
                this,
                "Choose a priority for #" + id,
                "Change priority",
                JOptionPane.PLAIN_MESSAGE,
                null,
                Priority.values(),
                Priority.valueOf(row.path("priority").asText()));
    if (picked != null) change("Priority update", () -> session.priority(id, picked));
  }

  public void closeIncident() {
    var row = selected(dashboard.open);
    if (row == null) return;
    String id = row.path("id").asText();
    String reason = note("Public resolution for #" + id, true);
    if (reason != null
        && confirm("Close report #" + id + "? All service work must be finished first."))
      change("Report closure", () -> session.closeIncident(id, reason));
  }

  public void moreActions() {
    var row = selected(dashboard.open);
    if (row == null) return;
    String id = row.path("id").asText();
    String[] actions = {"Reject an unassigned report", "Cancel an eligible assignment"};
    Object action =
        JOptionPane.showInputDialog(
            this,
            "Choose an action for #" + id,
            "More actions",
            JOptionPane.PLAIN_MESSAGE,
            null,
            actions,
            actions[0]);
    if (action == null) return;
    if (action.equals(actions[0])) {
      String reason = note("Public rejection reason", true);
      if (reason != null && confirm("Reject this unassigned report?"))
        change("Report rejection", () -> session.reject(id, reason));
    } else
      task(
          "Loading assignments",
          () -> session.incidentDetails(id),
          details -> {
            List<JsonNode> choices = new ArrayList<>();
            for (var a : details.path("assignments"))
              if (Set.of("OFFERED", "ACCEPTED").contains(a.path("state").asText())) choices.add(a);
            if (choices.isEmpty()) {
              JOptionPane.showMessageDialog(this, "No cancellable offered/accepted assignment.");
              return;
            }
            String[] labels =
                choices.stream()
                    .map(
                        a ->
                            "#"
                                + a.path("id").asText()
                                + "  "
                                + RecordTable.words(a.path("serviceType").asText())
                                + "  "
                                + RecordTable.words(a.path("state").asText()))
                    .toArray(String[]::new);
            Object chosen =
                JOptionPane.showInputDialog(
                    this,
                    "Choose an assignment",
                    "Cancel assignment",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    labels,
                    labels[0]);
            if (chosen == null) return;
            JsonNode a = choices.get(Arrays.asList(labels).indexOf(chosen));
            String reason = note("Private cancellation reason", true);
            if (reason != null && confirm("Cancel " + chosen + "?"))
              change("Assignment cancellation", () -> session.cancel(a, reason));
          });
  }

  public void respond(boolean accept) {
    var row = selected(dashboard.open);
    if (row == null) return;
    String id = row.path("assignment").path("id").asText();
    String note = note(accept ? "Private acceptance note" : "Private decline reason", !accept);
    if (note != null && confirm((accept ? "Accept" : "Decline") + " assignment #" + id + "?"))
      change(
          accept ? "Offer acceptance" : "Offer decline", () -> session.respond(id, accept, note));
  }

  public void progress() {
    var row = selected(dashboard.open);
    if (row == null) return;
    var a = row.path("assignment");
    var next = DesktopSession.nextState(a.path("state").asText());
    if (next == null) return;
    String note = note("Private progress note", false);
    if (note != null
        && confirm(
            "Update assignment #"
                + a.path("id").asText()
                + " to "
                + RecordTable.words(next.name())
                + "?"))
      change("Progress update", () -> session.progress(a.path("id").asText(), note));
  }

  public void availability() {
    Availability[] choices = {Availability.AVAILABLE, Availability.UNAVAILABLE};
    Availability picked =
        (Availability)
            JOptionPane.showInputDialog(
                this,
                "Choose availability. Active work keeps a team busy.",
                "My team availability",
                JOptionPane.PLAIN_MESSAGE,
                null,
                choices,
                choices[0]);
    if (picked != null) change("Availability update", () -> session.availability(picked));
  }

  public void retry() {
    var row = selected(dashboard.saved);
    if (row != null
        && confirm("Check / retry this saved original action? Its original request ID is reused."))
      change("Saved action confirmation", () -> session.retry(row.path("id").asText()));
  }

  public void account() {
    UserView user = session.user();
    JOptionPane.showMessageDialog(
        this,
        "Name: "
            + user.displayName()
            + "\nUsername: "
            + user.username()
            + "\nRole: "
            + user.role()
            + "\nAccount: #"
            + user.id()
            + "\nServer: "
            + session.endpoint,
        "My account",
        JOptionPane.INFORMATION_MESSAGE);
  }

  public void logout() {
    drafts.stop();
    if (dashboard.report != null) saveDraftSilently();
    task(
        "Signing out",
        () -> {
          session.logout();
          return true;
        },
        ok -> {
          setTitle("Emergency Help Network | Client");
          showSignIn("Signed out. Sign in as another user or close the window.");
        });
  }

  private void closeWindow() {
    if (busy) {
      JOptionPane.showMessageDialog(
          this,
          "Please wait for the current action to finish. Unconfirmed actions are saved locally.");
      return;
    }
    if (!confirm(
        "Close this client window? Saved drafts and unconfirmed actions remain on this laptop."))
      return;
    drafts.stop();
    if (dashboard != null && dashboard.report != null) saveDraftSilently();
    closing = true;
    updates.stop();
    setVisible(false);
    worker.submit(
        () -> {
          if (session != null) session.close();
          SwingUtilities.invokeLater(this::dispose);
        });
    worker.shutdown();
  }
}
