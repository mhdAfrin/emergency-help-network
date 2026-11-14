package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.server.data.*;
import lk.emergency.server.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ShortReferenceTest {
  @TempDir Path directory;

  @Test
  void referencesAreGlobalFiveDigitsAndSurviveRestart() throws Exception {
    Path file = directory.resolve("short.db");
    String last;
    try (Database db = new Database(file)) {
      AuthService auth = new AuthService(db);
      auth.seed();
      Set<String> ids =
          db.call(
              c -> {
                Set<String> result = new HashSet<>();
                for (String table : List.of("users", "teams"))
                  try (var s = c.createStatement();
                      var rows = s.executeQuery("SELECT id FROM " + table)) {
                    while (rows.next()) {
                      String id = rows.getString(1);
                      assertTrue(id.matches("[0-9]{5}"));
                      assertTrue(result.add(id));
                    }
                  }
                return result;
              });
      assertEquals(11, ids.size());
      last = db.transaction(ReferenceIds::next);
      assertTrue(last.matches("[0-9]{5}"));
    }
    try (Database db = new Database(file)) {
      assertTrue(Integer.parseInt(db.transaction(ReferenceIds::next)) > Integer.parseInt(last));
    }
  }

  @Test
  void concurrentAllocationsStayUniqueAndExhaustionIsExplicit() throws Exception {
    try (Database db = new Database(directory.resolve("parallel.db"))) {
      try (ExecutorService workers = Executors.newFixedThreadPool(8)) {
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 80; i++)
          results.add(workers.submit(() -> db.transaction(ReferenceIds::next)));
        Set<String> unique = new HashSet<>();
        for (var result : results) assertTrue(unique.add(result.get(5, TimeUnit.SECONDS)));
        assertEquals(80, unique.size());
      }
      db.transaction(
          c -> {
            try (var s = c.createStatement()) {
              s.executeUpdate("UPDATE reference_sequence SET next_value=99999");
            }
            return null;
          });
      assertEquals("99999", db.transaction(ReferenceIds::next));
      assertThrows(
          lk.emergency.common.DomainException.class, () -> db.transaction(ReferenceIds::next));
    }
  }
}
