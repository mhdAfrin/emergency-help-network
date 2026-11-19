package lk.emergency.client.desktop.view;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;
import lk.emergency.client.recovery.LocalJournal.Draft;
import lk.emergency.common.Types.Category;
import lk.emergency.common.desktop.DesktopTheme;

public final class ReportPanel extends JPanel {
  public final JComboBox<Category> category = new JComboBox<>(Category.values());
  public final JTextField address = new JTextField();
  public final JTextArea description = new JTextArea(5, 35);
  public final JLabel feedback =
      DesktopTheme.label("Use a fictional address for this academic demo.", 13, false);
  public final JButton submit, save;
  private Runnable changed = () -> {};
  private boolean loading;

  public ReportPanel(Runnable submit, Runnable save) {
    super(new BorderLayout(0, 12));
    setOpaque(false);
    JPanel card =
        DesktopTheme.card(
            "Report an incident",
            "Three simple fields. Your report goes to the operator immediately.");
    JPanel form = DesktopTheme.form();
    category.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            return super.getListCellRendererComponent(
                list, RecordTable.words(String.valueOf(value)), index, selected, focus);
          }
        });
    category.setName("Report category");
    address.setName("Address or landmark");
    description.setName("Report description");
    description.setLineWrap(true);
    description.setWrapStyleWord(true);
    description.setFont(address.getFont());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(3, 0, 5, 12);
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = .3;
    form.add(DesktopTheme.label("Category", 13, true), c);
    c.gridx = 1;
    c.weightx = .7;
    form.add(DesktopTheme.label("Address or landmark", 13, true), c);
    category.setPreferredSize(new Dimension(220, 35));
    address.setPreferredSize(new Dimension(430, 35));
    c.gridy = 1;
    c.gridx = 0;
    c.weightx = .3;
    form.add(category, c);
    c.gridx = 1;
    c.weightx = .7;
    form.add(address, c);
    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 2;
    c.weightx = 1;
    form.add(DesktopTheme.label("What happened?", 13, true), c);
    c.gridy = 3;
    JScrollPane text = new JScrollPane(description);
    text.setPreferredSize(new Dimension(600, 72));
    form.add(text, c);
    JScrollPane scroll = new JScrollPane(form);
    scroll.setBorder(null);
    scroll.getViewport().setBackground(Color.WHITE);
    JPanel content = new JPanel(new BorderLayout(0, 6));
    content.setOpaque(false);
    content.add(scroll, BorderLayout.CENTER);
    feedback.setForeground(DesktopTheme.MUTED);
    content.add(feedback, BorderLayout.SOUTH);
    card.add(content, BorderLayout.CENTER);
    this.submit = DesktopTheme.button("Submit report", true, submit);
    this.save = DesktopTheme.button("Save draft", false, save);
    card.add(DesktopTheme.row(this.submit, this.save), BorderLayout.SOUTH);
    add(card, BorderLayout.CENTER);
    DocumentListener listener =
        new DocumentListener() {
          public void insertUpdate(DocumentEvent e) {
            touch();
          }

          public void removeUpdate(DocumentEvent e) {
            touch();
          }

          public void changedUpdate(DocumentEvent e) {
            touch();
          }
        };
    address.getDocument().addDocumentListener(listener);
    description.getDocument().addDocumentListener(listener);
    category.addActionListener(e -> touch());
  }

  private void touch() {
    if (!loading) changed.run();
  }

  public void onChange(Runnable action) {
    changed = action;
  }

  public Draft value() {
    return new Draft(
        ((Category) category.getSelectedItem()).name(),
        address.getText().strip(),
        description.getText().strip());
  }

  public void load(Draft draft) {
    loading = true;
    try {
      category.setSelectedItem(
          draft == null ? Category.MEDICAL : Category.valueOf(draft.category()));
      address.setText(draft == null ? "" : draft.address());
      description.setText(draft == null ? "" : draft.description());
    } finally {
      loading = false;
    }
  }
}
