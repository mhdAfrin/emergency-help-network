package lk.emergency.client.desktop;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.*;
import lk.emergency.client.desktop.view.*;
import lk.emergency.client.recovery.LocalJournal.Draft;
import lk.emergency.common.*;
import lk.emergency.common.desktop.DesktopTheme;
import org.junit.jupiter.api.Test;

class DesktopViewTest {
  @Test
  void sortedTableSelectionKeepsCorrectServerReferenceAndDisablesEditing() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          DesktopTheme.install();
          var view = new RecordTable(RecordTable.Kind.INCIDENT);
          view.data(
              Json.tree(
                  java.util.List.of(
                      java.util.Map.of("id", "10002", "category", "FIRE", "state", "REPORTED"),
                      java.util.Map.of("id", "10001", "category", "MEDICAL", "state", "ACTIVE"))));
          view.table.getRowSorter().toggleSortOrder(0);
          view.table.setRowSelectionInterval(0, 0);
          assertEquals("10001", view.selected().path("id").asText());
          assertFalse(view.table.isCellEditable(0, 0));
          assertFalse(DesktopTheme.wrap("<script>").contains("<script>"));
        });
  }

  @Test
  void restoringDraftDoesNotTriggerAnAutosaveAndKeepsAddressOnlyFields() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var panel = new ReportPanel(() -> {}, () -> {});
          var changes = new java.util.concurrent.atomic.AtomicInteger();
          panel.onChange(changes::incrementAndGet);
          panel.load(new Draft("FIRE", "Fictional lab", "Restored text"));
          assertEquals(0, changes.get());
          assertEquals("Fictional lab", panel.value().address());
          panel.address.setText("Edited fictional lab");
          assertTrue(changes.get() > 0);
          assertEquals("FIRE", panel.value().category());
          panel.setSize(870, 285);
          layout(panel);
          var button =
              SwingUtilities.convertRectangle(
                  panel.submit.getParent(), panel.submit.getBounds(), panel);
          assertTrue(
              button.height > 0 && button.y >= 0 && button.getMaxY() <= panel.getHeight(),
              "Submit must stay inside the small report viewport");
        });
  }

  private static void layout(java.awt.Container container) {
    container.doLayout();
    for (var component : container.getComponents())
      if (component instanceof java.awt.Container child) layout(child);
  }
}
