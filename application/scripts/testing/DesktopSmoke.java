import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import lk.emergency.client.desktop.*;
import lk.emergency.client.desktop.view.*;
import lk.emergency.common.desktop.DesktopTheme;
import lk.emergency.server.data.Database;
import lk.emergency.server.desktop.ServerDesktop;
import lk.emergency.server.network.TcpServer;
import lk.emergency.server.service.AuthService;

/**
 * Programmer-driven Swing smoke test against an isolated real TCP server; no OS input injection.
 */
public class DesktopSmoke {
  static final List<ClientDesktop> windows = new ArrayList<>();
  static Path evidence;

  static <T> T edt(Callable<T> action) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return action.call();
    var task = new FutureTask<T>(action);
    SwingUtilities.invokeAndWait(task);
    return task.get();
  }

  @SuppressWarnings("unchecked")
  static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }

  static void await(Callable<Boolean> ready, String label) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
    while (System.nanoTime() < deadline) {
      if (edt(ready)) return;
      Thread.sleep(70);
    }
    throw new AssertionError("Timed out: " + label);
  }

  static ClientDashboard dashboard(ClientDesktop app) throws Exception {
    return field(app, "dashboard");
  }

  static void idle(ClientDesktop app) throws Exception {
    await(() -> !((Boolean) field(app, "busy")), "background task finishes");
  }

  static ClientDesktop open(String username, int port) throws Exception {
    ClientDesktop app =
        edt(
            () -> {
              var frame = new ClientDesktop();
              frame.setVisible(true);
              SignInPanel login = field(frame, "signIn");
              login.host.setText("127.0.0.1");
              login.port.setText(Integer.toString(port));
              return frame;
            });
    windows.add(app);
    if (username.equals("citizen1")) screenshot(app, "connect-sign-in");
    edt(
        () -> {
          SignInPanel login = field(app, "signIn");
          login.connect.doClick();
          return null;
        });
    idle(app);
    signin(app, username);
    return app;
  }

  static void signin(ClientDesktop app, String username) throws Exception {
    await(
        () -> {
          SignInPanel login = field(app, "signIn");
          return login != null && login.login.isEnabled();
        },
        "connection is ready to sign in");
    edt(
        () -> {
          SignInPanel login = field(app, "signIn");
          login.username.setText(username);
          login.password.setText("DemoPass123!");
          login.login.doClick();
          return null;
        });
    await(() -> dashboard(app) != null, "authorized desktop dashboard");
    idle(app);
  }

  static JOptionPane pane(Container root) {
    for (Component component : root.getComponents()) {
      if (component instanceof JOptionPane pane) return pane;
      if (component instanceof Container child) {
        JOptionPane found = pane(child);
        if (found != null) return found;
      }
    }
    return null;
  }

  static JTextArea area(Container root) {
    for (Component component : root.getComponents()) {
      if (component instanceof JTextArea area) return area;
      if (component instanceof Container child) {
        JTextArea found = area(child);
        if (found != null) return found;
      }
    }
    return null;
  }

  static JDialog dialog(String title) {
    for (Window window : Window.getWindows())
      if (window instanceof JDialog d && d.isShowing() && d.getTitle().equals(title)) return d;
    return null;
  }

  static void modal(String title, String text) throws Exception {
    await(() -> dialog(title) != null, "dialog " + title);
    edt(
        () -> {
          var dialog = dialog(title);
          var pane = pane(dialog);
          if (pane == null) throw new AssertionError("No JOptionPane for " + title);
          if (pane.getWantsInput()) {
            Object selected = null;
            for (Object item : pane.getSelectionValues())
              if (text == null || item.toString().contains(text)) {
                selected = item;
                break;
              }
            if (selected == null) throw new AssertionError("No matching choice: " + text);
            pane.setInputValue(selected);
          } else if (text != null) {
            var input = area(dialog);
            if (input != null) input.setText(text);
          }
          pane.setValue(JOptionPane.OK_OPTION);
          return null;
        });
  }

  static void select(ClientDesktop app) throws Exception {
    await(() -> dashboard(app).open.sizeOfData() > 0, "table receives report/assignment");
    idle(app);
    edt(
        () -> {
          dashboard(app).open.table.setRowSelectionInterval(0, 0);
          return null;
        });
  }

  static void action(Runnable handler) {
    SwingUtilities.invokeLater(handler);
  }

  static void screenshot(JFrame frame, String name) throws Exception {
    edt(
        () -> {
          JRootPane root = frame.getRootPane();
          BufferedImage image =
              new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
          Graphics2D graphics = image.createGraphics();
          root.printAll(graphics);
          graphics.dispose();
          ImageIO.write(image, "png", evidence.resolve(name + ".png").toFile());
          return null;
        });
  }

  static void serverControls(Path scratch) throws Exception {
    int port;
    try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
    System.setProperty("ehn.ui.database",scratch.resolve("server-window.db").toString());
    ServerDesktop frame=edt(()->{var window=new ServerDesktop();window.setVisible(true);return window;});
    try {
      int chosen=port;
      edt(()->{((JSpinner)field(frame,"port")).setValue(chosen);((JButton)field(frame,"start")).doClick();return null;});
      await(()->field(frame,"server")!=null&&!((Boolean)field(frame,"busy")),"server control starts listener");
      lk.emergency.client.network.ServerProbe.check("127.0.0.1",port);
      await(()->((JLabel)field(frame,"reports")).getText().contains("total"),"server counts refresh");
      screenshot(frame,"server-control");
      action(()->{try{((JButton)field(frame,"stop")).doClick();}catch(Exception error){throw new RuntimeException(error);}});
      modal("Stop server",null);
      await(()->field(frame,"server")==null&&!((Boolean)field(frame,"busy")),"server control stops listener");
      System.out.println("PASS: desktop server Start/Stop buttons seed the isolated database, bind TCP, answer EHN PING and refresh session/report counts.");
    } finally {
      TcpServer active=field(frame,"server");if(active!=null)active.close();
      Database db=field(frame,"database");if(db!=null)db.close();
      edt(()->{((javax.swing.Timer)field(frame,"monitor")).stop();((ExecutorService)field(frame,"worker")).shutdownNow();frame.dispose();return null;});
      System.clearProperty("ehn.ui.database");
    }
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
    evidence = root.getParent().resolve("docs/verification/ui/screenshots");
    Files.createDirectories(evidence);
    Path scratch = Files.createTempDirectory(root.resolve(".local"), "desktop-smoke-");
    DesktopTheme.install();
    Database db = new Database(scratch.resolve("ui.db"));
    AuthService auth = new AuthService(db);
    auth.seed();
    TcpServer server = new TcpServer("0.0.0.0", 0, 32, db, auth);
    int port = server.port();
    // Isolate endpoint settings/journals from the real evaluation demo by running in this fixture
    // directory.
    try {
      serverControls(scratch);
      ClientDesktop citizen = open("citizen1", port),
          operator = open("operator", port),
          fire = open("fire1", port);
      idle(citizen);
      screenshot(citizen, "citizen-report");
      edt(
          () -> {
            var report = dashboard(citizen).report;
            report.category.setSelectedItem(lk.emergency.common.Types.Category.FIRE);
            report.address.setText("Fictional campus laboratory");
            report.description.setText(
                "UI rehearsal only. Please coordinate the simulated fire team.");
            return null;
          });
      action(
          () -> {
            try {
              dashboard(citizen).report.submit.doClick();
            } catch (Exception error) {
              throw new RuntimeException(error);
            }
          });
      modal("Confirm action", null);
      select(operator);
      select(citizen);
      screenshot(operator, "operator-reports");
      action(operator::assign);
      modal("Assign team", "fire1 simulated team");
      modal("Confirm action", null);
      select(fire);
      if(edt(()->dashboard(fire).history.sizeOfData())!=0)throw new AssertionError("Active offers must not appear in the responder History tab");
      screenshot(fire, "responder-offer");
      action(() -> fire.respond(true));
      modal("Private acceptance note (optional)", "Private GUI test note");
      modal("Confirm action", null);
      await(
          () ->
              dashboard(fire).open.selected() != null
                  && dashboard(fire)
                      .open
                      .selected()
                      .path("assignment")
                      .path("state")
                      .asText()
                      .equals("ACCEPTED"),
          "accepted state updates live");
      idle(fire);
      action(fire::progress);
      modal("Private progress note (optional)", null);
      modal("Confirm action", null);
      await(
          () ->
              dashboard(fire)
                  .open
                  .selected()
                  .path("assignment")
                  .path("state")
                  .asText()
                  .equals("EN_ROUTE"),
          "en route state");
      action(
          () -> {
            try {
              citizen.details(dashboard(citizen).open, false);
            } catch (Exception error) {
              throw new RuntimeException(error);
            }
          });
      await(() -> dialog("Report details") != null, "details open before connection loss");
      for (var app : windows) idle(app);
      server.close();
      for (var app : windows)
        await(() -> dashboard(app) == null, "disconnect clears authenticated dashboard");
      if (edt(() -> dialog("Report details") != null))
        throw new AssertionError(
            "Owned details must close when the authenticated dashboard is cleared");
      server = new TcpServer("0.0.0.0", port, 32, db, auth);
      signin(operator, "operator");
      signin(citizen, "citizen1");
      signin(fire, "fire1");
      select(fire);
      await(
          () ->
              dashboard(fire)
                  .open
                  .selected()
                  .path("assignment")
                  .path("state")
                  .asText()
                  .equals("EN_ROUTE"),
          "snapshot restores active progress");
      for (String state : List.of("ARRIVED", "COMPLETED")) {
        action(fire::progress);
        modal("Private progress note (optional)", null);
        modal("Confirm action", null);
        if (!state.equals("COMPLETED"))
          await(
              () ->
                  dashboard(fire).open.selected() != null
                      && dashboard(fire)
                          .open
                          .selected()
                          .path("assignment")
                          .path("state")
                          .asText()
                          .equals(state),
              "progress " + state);
        else
          await(
              () -> dashboard(fire).history.sizeOfData() == 1,
              "completed assignment moves to history");
        idle(fire);
      }
      select(operator);
      action(operator::closeIncident);
      modal(
          "Public resolution for #"
              + edt(() -> dashboard(operator).open.selected().path("id").asText())
              + " (required)",
          "Simulation complete; fictional laboratory safe");
      modal("Confirm action", null);
      await(
          () -> dashboard(citizen).history.sizeOfData() == 1,
          "citizen closed history updates live");
      idle(citizen);
      edt(
          () -> {
            dashboard(citizen).tabs.setSelectedIndex(2);
            return null;
          });
      screenshot(citizen, "citizen-history");
      edt(
          () -> {
            dashboard(citizen).history.table.setRowSelectionInterval(0, 0);
            return null;
          });
      action(
          () -> {
            try {
              citizen.details(dashboard(citizen).history, false);
            } catch (Exception error) {
              throw new RuntimeException(error);
            }
          });
      await(() -> dialog("Report details") != null, "details window opens");
      edt(
          () -> {
            var d = dialog("Report details");
            var image =
                new BufferedImage(
                    d.getRootPane().getWidth(),
                    d.getRootPane().getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            d.getRootPane().printAll(g);
            g.dispose();
            ImageIO.write(image, "png", evidence.resolve("public-details.png").toFile());
            d.dispose();
            return null;
          });
      edt(
          () -> {
            citizen.setSize(980, 710);
            return null;
          });
      screenshot(citizen, "minimum-window");
      System.out.println(
          "PASS: real Swing forms/buttons/dialogs over TCP: citizen report, operator row/dropdown"
              + " assignment, responder acceptance and ordered progress, closure, live history,"
              + " details and minimum-size layout.");
      System.out.println(
          "PASS: server restart removes all authenticated dashboards, reconnect enables sign-in,"
              + " and authorized snapshots restore EN_ROUTE before completion.");
      System.out.println(
          "Screenshots are actual Swing component renders from isolated generated data, not"
              + " mockups. One physical laptop; no separate-laptop Wi-Fi or OS keystroke automation"
              + " claimed.");
    } finally {
      for (var app : windows) {
        try {
          DesktopSession session = field(app, "session");
          if (session != null) session.close();
        } catch (Exception ignored) {
        }
      }
      edt(
          () -> {
            for (var app : windows) {
              ((javax.swing.Timer) field(app, "updates")).stop();
              ((javax.swing.Timer) field(app, "drafts")).stop();
              ((ExecutorService) field(app, "worker")).shutdownNow();
              app.dispose();
            }
            for (Window window : Window.getWindows()) window.dispose();
            return null;
          });
      server.close();
      db.close();
    }
  }
}
