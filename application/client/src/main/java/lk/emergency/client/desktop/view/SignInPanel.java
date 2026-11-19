package lk.emergency.client.desktop.view;

import java.awt.*;
import javax.swing.*;
import lk.emergency.common.desktop.DesktopTheme;

/**
 * Connection and sign-in live on one small screen; credentials are never written to endpoint
 * settings.
 */
public final class SignInPanel extends JPanel {
  public final JTextField host = new JTextField("127.0.0.1"),
      port = new JTextField("5050"),
      username = new JTextField();
  public final JPasswordField password = new JPasswordField();
  public final JLabel message =
      DesktopTheme.label("Start the server, then connect using its Wi-Fi IP.", 13, false);
  public final JButton connect, login;

  public SignInPanel(Runnable onConnect, Runnable onLogin, Runnable onRegister) {
    super(new GridBagLayout());
    JPanel shell = new JPanel(new GridLayout(1, 2, 22, 0));
    shell.setOpaque(false);
    shell.setPreferredSize(new Dimension(850, 495));
    JPanel welcome = new JPanel(new BorderLayout(12, 16));
    welcome.setBackground(DesktopTheme.NAVY);
    welcome.setBorder(BorderFactory.createEmptyBorder(32, 26, 30, 26));
    JPanel branding = DesktopTheme.stack();
    JLabel badge = DesktopTheme.label("EHN  /  DESKTOP", 13, true);
    badge.setForeground(new Color(123, 210, 215));
    branding.add(badge);
    branding.add(Box.createVerticalStrut(28));
    JLabel title = DesktopTheme.label("<html>Emergency<br>Help Network</html>", 30, true);
    title.setForeground(Color.WHITE);
    branding.add(title);
    branding.add(Box.createVerticalStrut(20));
    JLabel info =
        DesktopTheme.label(
            "<html><body style='width:245px'>One shared server.<br>Clear updates for every"
                + " role.<br><br>Citizen: report and follow progress.<br><br>Operator: choose a"
                + " team and coordinate.<br><br>Responder: accept and update"
                + " progress.</body></html>",
            14,
            false);
    info.setForeground(new Color(208, 222, 241));
    branding.add(info);
    welcome.add(branding, BorderLayout.NORTH);
    JLabel academic = DesktopTheme.label("Academic demonstration only", 12, false);
    academic.setForeground(new Color(167, 192, 218));
    welcome.add(academic, BorderLayout.SOUTH);
    JPanel right =
        DesktopTheme.card("Connect and sign in", "Use the server laptop's IP for a Wi-Fi demo.");
    JPanel form = DesktopTheme.form();
    host.setName("Server IP address");
    port.setName("Server port");
    username.setName("Username");
    password.setName("Password");
    JPanel endpoint = new JPanel(new BorderLayout(8, 0));
    endpoint.setOpaque(false);
    port.setPreferredSize(new Dimension(72, 35));
    endpoint.add(host, BorderLayout.CENTER);
    endpoint.add(port, BorderLayout.EAST);
    DesktopTheme.field(form, 0, "Server IP address / TCP port", endpoint);
    connect = DesktopTheme.button("Connect / check", false, onConnect);
    DesktopTheme.field(form, 1, "1. Check the connection", connect);
    DesktopTheme.field(form, 2, "2. Username", username);
    DesktopTheme.field(form, 3, "Password", password);
    right.add(form, BorderLayout.CENTER);
    login = DesktopTheme.button("Sign in", true, onLogin);
    login.setEnabled(false);
    JPanel actions = DesktopTheme.stack();
    actions.add(DesktopTheme.row(login, DesktopTheme.button("New citizen", false, onRegister)));
    message.setForeground(DesktopTheme.MUTED);
    message.setText("<html>Start the server, then connect.<br>Demo password: DemoPass123!</html>");
    actions.add(message);
    right.add(actions, BorderLayout.SOUTH);
    shell.add(welcome);
    shell.add(right);
    add(shell);
    password.addActionListener(
        e -> {
          if (login.isEnabled()) onLogin.run();
        });
  }

  public void connected(boolean yes, String text) {
    login.setEnabled(yes);
    message.setText(DesktopTheme.wrap(text));
    message.setForeground(yes ? DesktopTheme.GREEN : DesktopTheme.MUTED);
  }
}
