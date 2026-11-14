package lk.emergency.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lk.emergency.client.recovery.LocalJournal;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.UserView;
import lk.emergency.common.Types.Role;
import org.junit.jupiter.api.Test;

public class PresentationTest {
  @Test
  void cardsShowUsefulFieldsWithoutProtocolNoiseOrCoordinates() {
    String output =
        String.join(
            "\n",
            RecordPrinter.lines(
                Json.tree(
                    Map.of(
                        "id",
                        "10024",
                        "category",
                        "FIRE",
                        "state",
                        "REPORTED",
                        "address",
                        "Campus",
                        "latitude",
                        6.1,
                        "longitude",
                        80.1,
                        "revision",
                        42,
                        "citizenId",
                        "10002"))));
    assertTrue(output.contains("INCIDENT #10024"));
    assertTrue(output.contains("Reported"));
    assertTrue(output.contains("Campus"));
    for (String internal : List.of("latitude", "longitude", "revision", "citizenId", "{", "}"))
      assertFalse(output.contains(internal));
  }

  @Test
  void everyRoleHasSevenMainChoicesWithoutPingOrDebugCommands() {
    PrintStream previous = System.out;
    try {
      for (Role role : Role.values()) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        new MenuRenderer(new ConsoleIO()).show(new UserView("10000", "demo", "Demo", role));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(7, output.lines().filter(line -> line.matches("  [0-6]  .*")).count());
        assertFalse(output.contains("Ping"));
        assertFalse(output.contains("Private test"));
        assertFalse(output.contains("90"));
        assertFalse(output.contains("â€”"));
      }
    } finally {
      System.setOut(previous);
    }
  }

  @Test
  void numericAccountCanRecoverPendingActionsAndCoordinateFreeDraft() throws Exception {
    Path folder = Files.createTempDirectory("numeric-journal");
    LocalJournal first = new LocalJournal(folder, "localhost:5050");
    Envelope action =
        Envelope.request(
            "CREATE_INCIDENT",
            UUID.randomUUID().toString(),
            Map.of("category", "FIRE", "address", "Campus", "description", "Demo"));
    first.put("10001", action);
    first.draft("10001", new LocalJournal.Draft("FIRE", "Campus", "Saved draft"));
    LocalJournal reopened = new LocalJournal(folder, "localhost:5050");
    assertEquals(action, reopened.list("10001").getFirst().request());
    assertEquals("Campus", reopened.draft("10001").address());
    assertTrue(reopened.list("10002").isEmpty());
    assertThrows(IOException.class, () -> reopened.list("../other"));
  }
}
