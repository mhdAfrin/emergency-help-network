package lk.emergency.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalJournalTest {
  @TempDir Path dir;
  String owner = UUID.randomUUID().toString();

  Envelope request() {
    return Envelope.request(
        "CREATE_INCIDENT",
        UUID.randomUUID().toString(),
        Map.of("description", "Keep this fictional input"));
  }

  @Test
  void pendingEnvelopeAndDraftSurviveNewJournalInstanceAndAreUserIsolated() throws Exception {
    LocalJournal first = new LocalJournal(dir, "localhost:5050");
    Envelope request = request();
    first.put(owner, request);
    first.draft(
        owner, new LocalJournal.Draft("FIRE", "Saved campus", "Saved description", "6.1", "80.1"));
    LocalJournal recovered = new LocalJournal(dir, "localhost:5050");
    assertEquals(request, recovered.list(owner).getFirst().request());
    assertEquals("Saved campus", recovered.draft(owner).address());
    String other = UUID.randomUUID().toString();
    assertTrue(recovered.list(other).isEmpty());
    assertNull(recovered.draft(other));
    assertTrue(new LocalJournal(dir, "another-host:5050").list(owner).isEmpty());
    recovered.remove(owner, request.requestId());
    assertTrue(first.list(owner).isEmpty());
    recovered.clearDraft(owner);
    assertNull(first.draft(owner));
  }

  @Test
  void credentialsCannotBeJournaledAndCapacityIsBounded() throws Exception {
    LocalJournal journal = new LocalJournal(dir, "host:5050");
    assertThrows(
        IOException.class,
        () ->
            journal.put(
                owner,
                Envelope.request(
                    "LOGIN", UUID.randomUUID().toString(), Map.of("password", "Never save this"))));
    for (int i = 0; i < 20; i++) journal.put(owner, request());
    assertThrows(IOException.class, () -> journal.put(owner, request()));
    assertEquals(20, journal.list(owner).size());
  }

  @Test
  void separateWritersMergeInsteadOfOverwritingPendingIds() throws Exception {
    LocalJournal a = new LocalJournal(dir, "host:5050"), b = new LocalJournal(dir, "host:5050");
    Envelope first = request(), second = request();
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?>
          x =
              workers.submit(
                  () -> {
                    try {
                      a.put(owner, first);
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  }),
          y =
              workers.submit(
                  () -> {
                    try {
                      b.put(owner, second);
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  });
      x.get(3, TimeUnit.SECONDS);
      y.get(3, TimeUnit.SECONDS);
      assertEquals(2, a.list(owner).size());
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void corruptJournalIsReportedInsteadOfSilentlyDiscarded() throws Exception {
    LocalJournal journal = new LocalJournal(dir, "host:5050");
    journal.put(owner, request());
    Path file;
    try (var files = Files.walk(dir)) {
      file =
          files
              .filter(p -> p.getFileName().toString().equals(owner + ".json"))
              .findFirst()
              .orElseThrow();
    }
    Files.writeString(file, "{broken");
    assertThrows(IOException.class, () -> journal.list(owner));
    assertEquals("{broken", Files.readString(file));
  }
}
