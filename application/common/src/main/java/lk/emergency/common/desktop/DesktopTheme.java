package lk.emergency.common.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/** Small shared Swing palette and layout helpers; no third-party UI framework. */
public final class DesktopTheme {
  public static final Color NAVY = new Color(22, 39, 65),
      BLUE = new Color(41, 99, 214),
      INK = new Color(36, 51, 73),
      MUTED = new Color(100, 115, 136),
      BACKGROUND = new Color(244, 247, 251),
      LINE = new Color(220, 227, 237),
      GREEN = new Color(14, 116, 95),
      RED = new Color(179, 56, 67);

  private DesktopTheme() {}

  public static void install() {
    try {
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (Exception ignored) {
    }
    Font font = new Font("Segoe UI", Font.PLAIN, 14);
    for (Object key : UIManager.getDefaults().keySet().toArray())
      if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
        UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
    UIManager.put("Panel.background", BACKGROUND);
    UIManager.put("Label.foreground", INK);
    UIManager.put("TextField.background", Color.WHITE);
    UIManager.put("PasswordField.background", Color.WHITE);
    UIManager.put("ComboBox.background", Color.WHITE);
    UIManager.put("TabbedPane.selected", Color.WHITE);
    UIManager.put("OptionPane.background", BACKGROUND);
    UIManager.put("ScrollPane.border", new LineBorder(LINE));
    UIManager.put("ToolTip.background", new Color(255, 252, 231));
  }

  public static JLabel label(String text, int size, boolean bold) {
    JLabel label = new JLabel(text);
    label.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
    label.setForeground(INK);
    return label;
  }

  public static JPanel card(String title, String subtitle) {
    JPanel panel = new JPanel(new BorderLayout(10, 12));
    panel.setBackground(Color.WHITE);
    panel.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(18, 20, 18, 20)));
    JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4));
    heading.setOpaque(false);
    heading.add(label(title, 20, true));
    if (subtitle != null && !subtitle.isBlank()) {
      JLabel small = label(subtitle, 13, false);
      small.setForeground(MUTED);
      heading.add(small);
    }
    panel.add(heading, BorderLayout.NORTH);
    return panel;
  }

  public static JPanel stack() {
    JPanel panel = new JPanel();
    panel.setOpaque(false);
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  public static JPanel row(Component... components) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
    panel.setOpaque(false);
    for (Component component : components) panel.add(component);
    return panel;
  }

  public static JPanel form() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    return panel;
  }

  public static void field(JPanel panel, int row, String text, JComponent component) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row * 2;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(6, 0, 5, 0);
    panel.add(label(text, 13, true), c);
    c.gridy++;
    c.insets = new Insets(0, 0, 9, 0);
    panel.add(component, c);
    component.setPreferredSize(new Dimension(360, component instanceof JScrollPane ? 95 : 35));
  }

  public static JButton button(String text, boolean primary, Runnable action) {
    JButton button =
        new JButton(text) {
          @Override
          protected void paintComponent(Graphics g) {
            Graphics2D draw = (Graphics2D) g.create();
            draw.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.setColor(
                !isEnabled()
                    ? LINE
                    : primary
                        ? (getModel().isRollover() ? BLUE.darker() : BLUE)
                        : new Color(234, 240, 249));
            draw.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            draw.dispose();
            super.paintComponent(g);
          }
        };
    button.setName(text);
    button.setBorder(new EmptyBorder(10, 16, 10, 16));
    button.setContentAreaFilled(false);
    button.setFocusPainted(true);
    button.setOpaque(false);
    button.setFont(new Font("Segoe UI", Font.BOLD, 13));
    button.setForeground(primary ? Color.WHITE : INK);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());
    return button;
  }

  public static void styleTable(JTable table) {
    table.setRowHeight(35);
    table.setShowGrid(false);
    table.setIntercellSpacing(new Dimension(0, 0));
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    table.setSelectionBackground(new Color(220, 234, 255));
    table.setSelectionForeground(INK);
    table.setFillsViewportHeight(true);
    table.setBackground(Color.WHITE);
    table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    table.getTableHeader().setBackground(new Color(236, 241, 248));
    table.getTableHeader().setPreferredSize(new Dimension(0, 36));
    table.setDefaultRenderer(
        Object.class,
        new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(
              JTable t, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, col);
            setBorder(new EmptyBorder(0, 10, 0, 8));
            if (!selected) {
              setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 253));
              setForeground(INK);
            }
            String text = String.valueOf(value);
            if (!selected
                && java.util.Set.of("Online", "Available", "Completed", "Closed", "Accepted")
                    .contains(text)) setForeground(GREEN);
            if (!selected
                && java.util.Set.of("Offline", "Rejected", "Declined", "Cancelled", "Unavailable")
                    .contains(text)) setForeground(RED);
            return this;
          }
        });
  }

  public static void error(Component parent, Throwable failure) {
    Throwable cause = failure;
    while ((cause instanceof java.util.concurrent.ExecutionException
            || cause instanceof java.util.concurrent.CompletionException)
        && cause.getCause() != null) cause = cause.getCause();
    String message =
        cause.getMessage() == null ? "The action could not be completed." : cause.getMessage();
    JOptionPane.showMessageDialog(
        parent,
        wrap(
            message
                + "\n\n"
                + "Refresh and check the latest state. Unconfirmed actions remain in Saved"
                + " actions."),
        "Please check",
        JOptionPane.WARNING_MESSAGE);
  }

  public static String wrap(String text) {
    return "<html><body style='width:390px;font-family:Segoe UI'>"
        + escape(text).replace("\n", "<br>")
        + "</body></html>";
  }

  public static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
