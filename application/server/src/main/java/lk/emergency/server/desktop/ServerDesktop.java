package lk.emergency.server.desktop;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import lk.emergency.common.desktop.DesktopTheme;
import lk.emergency.server.data.Database;
import lk.emergency.server.network.TcpServer;
import lk.emergency.server.service.AuthService;

/** One desktop window owns the listener/database; starting or stopping never blocks Swing. */
public final class ServerDesktop extends JFrame {
  private record Address(String ip, String adapter) {
    @Override
    public String toString() {
      return ip + "  -  " + adapter;
    }
  }

  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "desktop-server-work"));
  private volatile TcpServer server;
  private volatile Database database;
  private final Path data =
      Path.of(System.getProperty("ehn.ui.database", ".local/data/emergency-ui.db"));
  private final JSpinner port =
      new JSpinner(
          new SpinnerNumberModel(Integer.getInteger("ehn.ui.port", 5050).intValue(), 1, 65535, 1));
  private final JComboBox<Address> addresses = new JComboBox<>();
  private final JLabel state = DesktopTheme.label("Stopped", 24, true),
      sessions = DesktopTheme.label("0 connected clients", 20, true),
      reports = DesktopTheme.label("No server running", 18, true);
  private final JTextArea log = new JTextArea();
  private final JButton start, stop;
  private final javax.swing.Timer monitor;
  private boolean busy, closing;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          DesktopTheme.install();
          new ServerDesktop().setVisible(true);
        });
  }

  public ServerDesktop() {
    super("Emergency Help Network | Server control");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setSize(1010, 750);
    setMinimumSize(new Dimension(880, 650));
    setLocationByPlatform(true);
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(DesktopTheme.NAVY);
    header.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
    JLabel brand = DesktopTheme.label("EHN   Server control", 26, true);
    brand.setForeground(Color.WHITE);
    header.add(brand, BorderLayout.WEST);
    JLabel badge = DesktopTheme.label("ONE SERVER  /  MULTIPLE LAPTOPS", 11, true);
    badge.setForeground(new Color(130, 218, 210));
    header.add(badge, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout(14, 14));
    body.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
    JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
    cards.setOpaque(false);
    cards.add(metric("SERVER STATUS", state));
    cards.add(metric("CONNECTIONS", sessions));
    cards.add(metric("REPORTS", reports));
    body.add(cards, BorderLayout.NORTH);
    JPanel center = new JPanel(new GridLayout(1, 2, 14, 0));
    center.setOpaque(false);
    JPanel setup =
        DesktopTheme.card(
            "Start the shared server", "All clients connect to this laptop's Wi-Fi address.");
    JPanel form = DesktopTheme.form();
    port.setEditor(new JSpinner.NumberEditor(port, "0"));
    port.setName("TCP port");
    addresses.setName("Server network address");
    refreshAddresses();
    DesktopTheme.field(form, 0, "TCP port", port);
    DesktopTheme.field(form, 1, "Share this IP with other laptops", addresses);
    JPanel instructions = DesktopTheme.stack();
    instructions.add(
        DesktopTheme.label(
            "<html><body style='width:295px'>1. Use the same Wi-Fi on every laptop.<br>2. Start"
                + " this server; keep its window open.<br>3. Clients enter this IP and TCP"
                + " port.<br>4. Use a different account in each client.</body></html>",
            13,
            false));
    JPanel settings = new JPanel(new BorderLayout(0, 12));
    settings.setOpaque(false);
    settings.add(form, BorderLayout.NORTH);
    settings.add(instructions, BorderLayout.CENTER);
    setup.add(settings, BorderLayout.CENTER);
    start = DesktopTheme.button("Start server", true, this::startServer);
    stop = DesktopTheme.button("Stop server", false, () -> stopServer(false));
    stop.setEnabled(false);
    setup.add(DesktopTheme.row(start, stop), BorderLayout.SOUTH);
    center.add(setup);
    JPanel activity =
        DesktopTheme.card(
            "Server activity", "Status and connections. Business records remain in the database.");
    log.setName("Server activity log");
    log.setEditable(false);
    log.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    log.setLineWrap(true);
    log.setWrapStyleWord(true);
    log.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    activity.add(new JScrollPane(log), BorderLayout.CENTER);
    activity.add(
        DesktopTheme.row(
            DesktopTheme.button("Copy IP", false, this::copyIp),
            DesktopTheme.button("Refresh IPs", false, this::refreshAddresses),
            DesktopTheme.button("Wi-Fi help", false, this::networkHelp)),
        BorderLayout.SOUTH);
    center.add(activity);
    body.add(center, BorderLayout.CENTER);
    JLabel footer =
        DesktopTheme.label(
            "Academic demo  |  One central SQLite database  |  No real emergency agencies",
            12,
            false);
    footer.setForeground(DesktopTheme.MUTED);
    body.add(footer, BorderLayout.SOUTH);
    add(body, BorderLayout.CENTER);
    append("Ready. Click Start server. Demo accounts are prepared automatically.");
    append("Database: " + data.toAbsolutePath().normalize());
    monitor = new javax.swing.Timer(1800, e -> refreshCounts());
    monitor.start();
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            stopServer(true);
          }
        });
  }

  private JPanel metric(String title, JLabel value) {
    var panel = DesktopTheme.card(title, null);
    value.setForeground(title.equals("SERVER STATUS") ? DesktopTheme.MUTED : DesktopTheme.INK);
    panel.add(value, BorderLayout.CENTER);
    return panel;
  }

  private void append(String value) {
    SwingUtilities.invokeLater(
        () -> {
          String existing = log.getText();
          if (existing.length() > 16000) existing = existing.substring(existing.length() - 10000);
          log.setText(
              existing
                  + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                  + "  "
                  + value
                  + "\n");
          log.setCaretPosition(log.getDocument().getLength());
        });
  }

  private void refreshAddresses() {
    String previous = addresses.getSelectedItem() instanceof Address value ? value.ip() : null;
    addresses.removeAllItems();
    List<Address> values = new ArrayList<>();
    try {
      for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces()))
        if (adapter.isUp() && !adapter.isLoopback())
          for (InetAddress address : Collections.list(adapter.getInetAddresses()))
            if (address instanceof Inet4Address && !address.isLinkLocalAddress())
              values.add(new Address(address.getHostAddress(), adapter.getDisplayName()));
    } catch (Exception error) {
      append("Could not list adapters. Use ipconfig to find the Wi-Fi IPv4 address.");
    }
    values.sort(
        Comparator.comparing(
                (Address value) ->
                    !value
                        .adapter()
                        .toLowerCase(Locale.ROOT)
                        .matches(".*(wi-fi|wireless|802\\.11).*"))
            .thenComparing(Address::ip));
    values.forEach(addresses::addItem);
    if (values.isEmpty())
      addresses.addItem(
          new Address("127.0.0.1", "Same-laptop use only; connect Wi-Fi for other laptops"));
    if (previous != null)
      for (int index = 0; index < addresses.getItemCount(); index++)
        if (addresses.getItemAt(index).ip().equals(previous)) {
          addresses.setSelectedIndex(index);
          break;
        }
  }

  private void startServer() {
    if (busy || server != null) return;
    try {
      port.commitEdit();
    } catch (java.text.ParseException error) {
      DesktopTheme.error(this, new Exception("Enter a TCP port from 1 to 65535."));
      return;
    }
    int selected = (Integer) port.getValue();
    busy = true;
    start.setEnabled(false);
    port.setEnabled(false);
    state.setText("Starting...");
    append("Preparing demo accounts and opening TCP port " + selected + "...");
    worker.submit(
        () -> {
          Database next = null;
          try {
            next = new Database(data);
            AuthService auth = new AuthService(next);
            auth.seed();
            TcpServer listener = new TcpServer("0.0.0.0", selected, 32, next, auth);
            database = next;
            server = listener;
            SwingUtilities.invokeLater(
                () -> {
                  busy = false;
                  state.setText("Running");
                  state.setForeground(DesktopTheme.GREEN);
                  stop.setEnabled(true);
                  append(
                      "Listening on 0.0.0.0:"
                          + selected
                          + ". Clients use the selected Wi-Fi IP, not 0.0.0.0.");
                });
          } catch (Exception error) {
            if (next != null) next.close();
            SwingUtilities.invokeLater(
                () -> {
                  busy = false;
                  start.setEnabled(true);
                  port.setEnabled(true);
                  state.setText("Stopped");
                  state.setForeground(DesktopTheme.RED);
                  append("Startup failed: " + error.getMessage());
                  DesktopTheme.error(
                      this,
                      new Exception(
                          "Server could not start. Check whether another server is using this port."
                              + " Details: "
                              + error.getMessage()));
                });
          }
        });
  }

  private void refreshCounts() {
    TcpServer active = server;
    if (active == null || busy || closing) return;
    sessions.setText(active.sessionCount() + " connected clients");
    worker.submit(
        () -> {
          Database db = database;
          if (db == null) return;
          try {
            String result =
                db.call(
                    c -> {
                      try (var s = c.createStatement();
                          var rows =
                              s.executeQuery(
                                  "SELECT COUNT(*), SUM(CASE WHEN state IN ('REPORTED','ACTIVE')"
                                      + " THEN 1 ELSE 0 END) FROM incidents")) {
                        rows.next();
                        return rows.getInt(1) + " total / " + rows.getInt(2) + " open";
                      }
                    });
            SwingUtilities.invokeLater(
                () -> {
                  if (server == active) reports.setText(result);
                });
          } catch (Exception ignored) {
          }
        });
  }

  private void copyIp() {
    Address address = (Address) addresses.getSelectedItem();
    if (address != null) {
      Toolkit.getDefaultToolkit()
          .getSystemClipboard()
          .setContents(new StringSelection(address.ip()), null);
      append("Copied server IP " + address.ip() + ". Client port: " + port.getValue());
    }
  }

  private void networkHelp() {
    JOptionPane.showMessageDialog(
        this,
        DesktopTheme.wrap(
            "Use the same permitted Wi-Fi/hotspot on every laptop. Choose the Wi-Fi adapter, not"
                + " VPN/VirtualBox.\n\n"
                + "Other laptops enter this IP and matching port. 127.0.0.1 means their own"
                + " laptop.\n\n"
                + "If blocked, follow the included guide to allow Java/TCP on the SERVER's trusted"
                + " Private network. The optional ALLOW-LAN-FIREWALL.cmd must be run as"
                + " administrator. No router port-forwarding is needed.\n\n"
                + "Demo accounts: operator, citizen1, citizen2, police1, ambulance1, fire1, fire2."
                + " Password: DemoPass123!\n\n"
                + "Wi-Fi/guest-network isolation may prevent laptop-to-laptop connections."),
        "Wi-Fi demo help",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private void stopServer(boolean exit) {
    if (busy) {
      JOptionPane.showMessageDialog(this, "Please wait for startup/shutdown to finish.");
      return;
    }
    if (server != null
        && JOptionPane.showConfirmDialog(
                this,
                "Stop the shared server? Connected clients will need to reconnect and sign in"
                    + " again.",
                "Stop server",
                JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    if (server == null) {
      if (exit) finish();
      return;
    }
    busy = true;
    stop.setEnabled(false);
    state.setText("Stopping...");
    worker.submit(
        () -> {
          TcpServer active = server;
          Database db = database;
          server = null;
          database = null;
          if (active != null) active.close();
          if (db != null) db.close();
          SwingUtilities.invokeLater(
              () -> {
                busy = false;
                state.setText("Stopped");
                state.setForeground(DesktopTheme.MUTED);
                sessions.setText("0 connected clients");
                reports.setText("Records saved");
                start.setEnabled(true);
                port.setEnabled(true);
                append("Server stopped. Records preserved for the next start.");
                if (exit) finish();
              });
        });
  }

  private void finish() {
    closing = true;
    monitor.stop();
    worker.shutdown();
    dispose();
  }
}
