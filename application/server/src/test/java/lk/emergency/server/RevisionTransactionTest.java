package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RevisionTransactionTest {
  @TempDir Path dir;

  @Test
  void atomicExpectedRevisionRejectsCompetingUpdate() throws Exception {
    try (Database db = new Database(dir.resolve("db"))) {
      new AuthService(db).seed();
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CyclicBarrier barrier = new CyclicBarrier(2);
      Callable<Boolean> update =
          () -> {
            barrier.await(3, TimeUnit.SECONDS);
            try {
              db.transaction(
                  c -> {
                    try (var p =
                        c.prepareStatement(
                            "UPDATE teams SET revision=revision+1 WHERE name='police1 simulated"
                                + " team' AND revision=?")) {
                      p.setLong(1, 0);
                      MutationSupport.requireUpdated(p.executeUpdate());
                    }
                    return null;
                  });
              return true;
            } catch (DomainException e) {
              assertEquals(Types.ErrorCode.VERSION_CONFLICT, e.code());
              return false;
            }
          };
      try {
        Future<Boolean> a = pool.submit(update), b = pool.submit(update);
        assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
      } finally {
        pool.shutdownNow();
      }
      assertEquals(
          Long.valueOf(1),
          db.call(
              c -> {
                try (var s = c.createStatement();
                    var rows =
                        s.executeQuery(
                            "SELECT revision FROM teams WHERE name='police1 simulated team'")) {
                  assertTrue(rows.next());
                  return rows.getLong(1);
                }
              }));
    }
  }

  @Test
  void concurrentDuplicateAndReplayAfterRestart() throws Exception {
    Path path = dir.resolve("db");
    String userId;
    Envelope request =
        Envelope.request("FUTURE_MUTATION", UUID.randomUUID().toString(), Map.of("test", true));
    AtomicInteger calls = new AtomicInteger();
    try (Database db = new Database(path)) {
      userId =
          new AuthService(db).register(new Registration("dedup", "Test", "password!", null)).id();
      MutationSupport helper = new MutationSupport(db);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Callable<MutationSupport.Result> action =
          () ->
              helper.execute(
                  userId,
                  request,
                  c -> {
                    calls.incrementAndGet();
                    return Envelope.success(request, Map.of("saved", true));
                  });
      try {
        Future<MutationSupport.Result> a = pool.submit(action), b = pool.submit(action);
        assertNotEquals(
            a.get(5, TimeUnit.SECONDS).replayed(), b.get(5, TimeUnit.SECONDS).replayed());
        assertEquals(1, calls.get());
      } finally {
        pool.shutdownNow();
      }
    }
    try (Database reopened = new Database(path)) {
      assertTrue(
          new MutationSupport(reopened)
              .execute(
                  userId,
                  request,
                  c -> {
                    fail("Saved response must survive restart");
                    return null;
                  })
              .replayed());
    }
  }
}
