package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import lk.emergency.server.config.ServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerSettingsTest {
  @TempDir Path dir;

  @Test void commandLineOverridesFileAndKeepsDatabaseAcrossSeedAndRun() throws Exception {
    Path file = dir.resolve("classroom.properties");
    Files.writeString(file, "server.host=127.0.0.1\nserver.port=5151\nserver.maxConnections=12\ndatabase.path=old.db\n");
    String[] arguments = {"--config", file.toString(), "--host", "0.0.0.0", "--port", "5252", "--database", dir.resolve("classroom.db").toString(), "--seed"};
    var settings = ServerSettings.load(arguments);
    assertEquals("0.0.0.0", settings.host()); assertEquals(5252, settings.port());
    assertEquals(12, settings.maxConnections()); assertTrue(settings.seed());
    assertEquals(dir.resolve("classroom.db"), settings.database());
    assertEquals(settings.database(), ServerSettings.load(java.util.Arrays.copyOf(arguments, arguments.length-1)).database());
  }

  @Test void missingExplicitConfigFailsRatherThanSilentlyUsingAnotherDatabase() {
    assertThrows(java.io.IOException.class, () -> ServerSettings.load(new String[]{"--config", dir.resolve("missing").toString()}));
  }

  @Test void invalidEndpointsAndUnknownOptionsAreRejected() {
    for (String value : new String[]{"0", "65536", "invalid"})
      assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(new String[]{"--port", value}));
    assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(new String[]{"--port"}));
    assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(new String[]{"--potr", "5050"}));
    assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(new String[]{"--max-connections", "201"}));
    assertThrows(IllegalArgumentException.class, () -> ServerSettings.load(new String[]{"--port", "5050", "--port", "5051"}));
  }
}
