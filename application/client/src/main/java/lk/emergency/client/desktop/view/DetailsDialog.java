package lk.emergency.client.desktop.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import javax.swing.*;
import lk.emergency.client.ui.ConsoleIO;
import lk.emergency.common.desktop.DesktopTheme;

/** Authorized details are presented as labelled text and public/own timelines, never raw JSON. */
public final class DetailsDialog {
  private DetailsDialog() {}

  public static void show(JFrame owner, JsonNode details, boolean assignment) {
    JsonNode incident = details.path("incident");
    JDialog dialog =
        new JDialog(owner, assignment ? "Assignment details" : "Report details", false);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JPanel body = new JPanel(new BorderLayout(12, 12));
    body.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    JTextArea summary = new JTextArea();
    summary.setEditable(false);
    summary.setLineWrap(true);
    summary.setWrapStyleWord(true);
    summary.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    summary.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    String text =
        "REPORT #"
            + incident.path("id").asText()
            + "\n\n"
            + "Category: "
            + RecordTable.words(incident.path("category").asText())
            + "\n"
            + "Status: "
            + RecordTable.words(incident.path("state").asText())
            + "   |   Priority: "
            + RecordTable.words(incident.path("priority").asText())
            + "\n"
            + "Address: "
            + incident.path("address").asText()
            + "\n"
            + "Description: "
            + incident.path("description").asText()
            + "\n"
            + "Reported: "
            + ConsoleIO.time(incident.path("createdAt").asText())
            + "\n";
    if (incident.path("publicResolutionReason").isTextual())
      text += "Resolution: " + incident.path("publicResolutionReason").asText() + "\n";
    if (assignment) {
      var row = details.path("assignment");
      text +=
          "\nASSIGNMENT #"
              + row.path("id").asText()
              + " | "
              + RecordTable.words(row.path("serviceType").asText())
              + " | "
              + RecordTable.words(row.path("state").asText());
    } else {
      text += "\nSERVICE ASSIGNMENTS\n";
      if (details.path("assignments").isEmpty()) text += "No team has been assigned yet.\n";
      for (var row : details.path("assignments"))
        text +=
            "#"
                + row.path("id").asText()
                + "  "
                + RecordTable.words(row.path("serviceType").asText())
                + "  -  "
                + RecordTable.words(row.path("state").asText())
                + "\n";
      // Operators receive internal notes; citizens never receive this field from the server.
      if (details.has("operationalNotes"))
        for (var row : details.path("operationalNotes"))
          text += "Operator note: " + row.path("internalNote").asText() + "\n";
    }
    summary.setText(text);
    summary.setCaretPosition(0);
    var timeline = new RecordTable(RecordTable.Kind.TIMELINE);
    timeline.data(details.path("timeline"));
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(summary), timeline);
    split.setResizeWeight(.57);
    split.setBorder(null);
    body.add(split, BorderLayout.CENTER);
    body.add(
        DesktopTheme.row(DesktopTheme.button("Close", false, dialog::dispose)), BorderLayout.SOUTH);
    dialog.add(body);
    dialog.setSize(870, 620);
    dialog.setMinimumSize(new Dimension(680, 480));
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }
}
