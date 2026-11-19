package lk.emergency.client.desktop.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import lk.emergency.client.ui.ConsoleIO;
import lk.emergency.common.desktop.DesktopTheme;

/**
 * Non-editable tables keep server references in the model, so users select rows instead of typing
 * IDs.
 */
public final class RecordTable extends JPanel {
  public enum Kind {
    INCIDENT,
    ASSIGNMENT,
    TEAM,
    TIMELINE,
    SAVED
  }

  public final JTable table = new JTable();
  private final DefaultTableModel model;
  private final Kind kind;
  private List<JsonNode> rows = List.of();
  private final JLabel count = DesktopTheme.label("Nothing to show yet", 12, false);

  public RecordTable(Kind kind) {
    super(new BorderLayout(0, 8));
    this.kind = kind;
    setOpaque(false);
    String[] columns =
        switch (kind) {
          case INCIDENT ->
              new String[] {
                "Reference", "Category", "Address / landmark", "Priority", "Status", "Updated"
              };
          case ASSIGNMENT ->
              new String[] {"Assignment", "Report", "Service", "Address / landmark", "Status"};
          case TEAM -> new String[] {"Team", "Name", "Service", "Availability", "Presence"};
          case TIMELINE -> new String[] {"Time", "Update", "Message"};
          case SAVED -> new String[] {"Saved action", "Status"};
        };
    model =
        new DefaultTableModel(columns, 0) {
          @Override
          public boolean isCellEditable(int row, int col) {
            return false;
          }
        };
    table.setModel(model);
    table.setName(kind.name() + " table");
    DesktopTheme.styleTable(table);
    add(new JScrollPane(table), BorderLayout.CENTER);
    count.setForeground(DesktopTheme.MUTED);
    add(count, BorderLayout.SOUTH);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting() && selected() != null)
                count.setText(
                    rows.size()
                        + " item(s)  |  "
                        + (kind == Kind.SAVED
                            ? "Saved action selected"
                            : "Selected #" + reference(selected())));
            });
  }

  public void data(JsonNode array) {
    String selectedId = selected() == null ? null : reference(selected());
    List<JsonNode> values = new ArrayList<>();
    array.forEach(x -> values.add(x.deepCopy()));
    rows = List.copyOf(values);
    model.setRowCount(0);
    for (JsonNode row : rows) {
      var a = row.has("assignment") ? row.path("assignment") : row;
      Object[] value =
          switch (kind) {
            case INCIDENT ->
                new Object[] {
                  row.path("id").asText(),
                  words(row.path("category").asText()),
                  row.path("address").asText(),
                  words(row.path("priority").asText()),
                  words(row.path("state").asText()),
                  ConsoleIO.time(row.path("updatedAt").asText())
                };
            case ASSIGNMENT ->
                new Object[] {
                  a.path("id").asText(),
                  a.path("incidentId").asText(),
                  words(a.path("serviceType").asText()),
                  row.path("incident").path("address").asText(),
                  words(a.path("state").asText())
                };
            case TEAM ->
                new Object[] {
                  row.path("id").asText(),
                  row.path("name").asText(),
                  words(row.path("serviceType").asText()),
                  words(row.path("availability").asText()),
                  words(row.path("presence").asText())
                };
            case TIMELINE ->
                new Object[] {
                  ConsoleIO.time(row.path("createdAt").asText()),
                  words(row.path("eventType").asText()),
                  row.path("publicMessage").asText()
                      + (row.path("ownPrivateNote").isTextual()
                          ? " | Private: " + row.path("ownPrivateNote").asText()
                          : "")
                };
            case SAVED -> new Object[] {words(row.path("type").asText()), "Awaiting confirmation"};
          };
      model.addRow(value);
    }
    count.setText(
        rows.isEmpty()
            ? "Nothing to show yet. New updates appear automatically."
            : rows.size() + " item(s)  |  Select a row to use its actions");
    if (selectedId != null)
      for (int index = 0; index < rows.size(); index++)
        if (reference(rows.get(index)).equals(selectedId)) {
          int view = table.convertRowIndexToView(index);
          if (view >= 0) table.setRowSelectionInterval(view, view);
          break;
        }
  }

  private String reference(JsonNode row) {
    return row.has("assignment")
        ? row.path("assignment").path("id").asText()
        : row.path("id").asText();
  }

  public JsonNode selected() {
    int row = table.getSelectedRow();
    return row < 0 ? null : rows.get(table.convertRowIndexToModel(row));
  }

  public int sizeOfData() {
    return rows.size();
  }

  public static String words(String value) {
    if (value == null || value.isEmpty()) return "-";
    String text = value.toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
