import static lk.emergency.common.Types.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import lk.emergency.client.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.*;

/** Twenty authenticated real TCP sessions; local monotonic commit-to-application-receipt timing. */
public class LocalLoadTest {
  record Receipt(Envelope event, long receivedAt) {}

  static Envelope ok(Envelope response) {
    if (!Boolean.TRUE.equals(response.ok())) throw new IllegalStateException(response.toString());
    return response;
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
    Path work = root.resolve(".local/testing/load-" + UUID.randomUUID());
    Files.createDirectories(work);
    Path evidence = root.getParent().resolve("docs/verification/evidence/member3-finalized");
    Files.createDirectories(evidence);
    AtomicLong committed = new AtomicLong();
    List<ConnectionManager> peers = new ArrayList<>();
    BlockingQueue<Receipt> events = new ArrayBlockingQueue<>(256);
    try (Database database = new Database(work.resolve("load.db"), committed::set)) {
      AuthService auth = new AuthService(database);
      auth.seed();
      try (TcpServer server = new TcpServer("127.0.0.1", 0, 32, database, auth)) {
        ExecutorService workers = Executors.newFixedThreadPool(20);
        try {
          for (int i = 0; i < 20; i++) {
            final int index = i;
            peers.add(
                new ConnectionManager(
                    "127.0.0.1",
                    server.port(),
                    event -> {
                      if (index == 19 && event.type().equals("INCIDENT_CREATED"))
                        events.add(new Receipt(event, System.nanoTime()));
                    }));
          }
          List<Future<?>> jobs = new ArrayList<>();
          CyclicBarrier barrier = new CyclicBarrier(20);
          for (int i = 0; i < 20; i++) {
            final int index = i;
            jobs.add(
                workers.submit(
                    () -> {
                      try {
                        ConnectionManager peer = peers.get(index);
                        String username = index == 19 ? "operator" : "load" + index;
                        if (index != 19)
                          ok(
                              peer.call(
                                  "REGISTER_CITIZEN",
                                  new Registration(username, username, "LoadPass123!", null)));
                        ok(
                            peer.call(
                                "LOGIN",
                                new Login(
                                    username, index == 19 ? "DemoPass123!" : "LoadPass123!")));
                        barrier.await(30, TimeUnit.SECONDS);
                        for (int n = 0; n < 25; n++) ok(peer.call("PING", Map.of()));
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
          }
          for (Future<?> job : jobs) job.get(45, TimeUnit.SECONDS);
          if (server.sessionCount() != 20)
            throw new IllegalStateException(
                "Expected 20 active sessions, got " + server.sessionCount());
          List<Long> latencies = new ArrayList<>();
          StringBuilder csv = new StringBuilder("sample,commit_nanos,receipt_nanos,latency_ms\n");
          for (int i = 0; i < 50; i++) {
            committed.set(0);
            Envelope response =
                ok(
                    peers
                        .get(0)
                        .call(
                            "CREATE_INCIDENT",
                            new IncidentInput(
                                Category.OTHER,
                                "Fictional load campus",
                                "Academic latency sample " + i,
                                null,
                                null)));
            Receipt receipt = events.poll(10, TimeUnit.SECONDS);
            if (receipt == null
                || !receipt.event().payload().get("id").equals(response.payload().get("id")))
              throw new IllegalStateException("Missing matching report event");
            long atCommit = committed.get(), delta = receipt.receivedAt() - atCommit;
            if (atCommit == 0 || delta < 0)
              throw new IllegalStateException("Invalid monotonic timing sample");
            latencies.add(delta);
            csv.append(
                String.format(
                    Locale.ROOT,
                    "%d,%d,%d,%.6f%n",
                    i + 1,
                    atCommit,
                    receipt.receivedAt(),
                    delta / 1e6));
          }
          Collections.sort(latencies);
          double median = (latencies.get(24) / 2.0 + latencies.get(25) / 2.0) / 1e6,
              p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1) / 1e6;
          String report =
              "PASS: 20 authenticated real TCP sessions, 500 PING exchanges, no failed requests.\n"
                  + "Measurement: 50 sequential CREATE_INCIDENT commits while 20 sessions stay"
                  + " connected.\n"
                  + "Start: System.nanoTime immediately after JDBC commit returns, inside the"
                  + " database executor.\n"
                  + "End: System.nanoTime in operator client's background event callback after"
                  + " decoding INCIDENT_CREATED.\n"
                  + "Server and headless clients share one JVM/monotonic clock but exchange actual"
                  + " loopback TCP frames.\n"
                  + "The measured interval excludes the database write itself; includes post-commit"
                  + " routing, queue, socket and decoding.\n"
                  + String.format(
                      Locale.ROOT,
                      "Samples: 50; median: %.6f ms; p95 (nearest rank): %.6f ms.\n",
                      median,
                      p95)
                  + "Runtime: "
                  + System.getProperty("java.runtime.version")
                  + "; VM: "
                  + System.getProperty("java.vm.name")
                  + "\n"
                  + "OS: "
                  + System.getProperty("os.name")
                  + " "
                  + System.getProperty("os.version")
                  + " "
                  + System.getProperty("os.arch")
                  + "\n"
                  + "CPU descriptor: "
                  + System.getenv("PROCESSOR_IDENTIFIER")
                  + "; available processors: "
                  + Runtime.getRuntime().availableProcessors()
                  + "\n"
                  + "JVM maximum heap bytes (not physical RAM): "
                  + Runtime.getRuntime().maxMemory()
                  + "\n"
                  + "Network: 127.0.0.1 loopback, one machine; not a separate-laptop LAN"
                  + " benchmark.\n"
                  + "Console observation is separate: ProcessSmoke exercises notifications while"
                  + " menu input is idle; these timings do not measure physical terminal"
                  + " repaint.\n";
          Files.writeString(evidence.resolve("load-latency.csv"), csv.toString());
          Files.writeString(evidence.resolve("load-latency.txt"), report);
          System.out.print(report);
        } finally {
          for (var peer : peers) peer.close();
          workers.shutdownNow();
          workers.awaitTermination(5, TimeUnit.SECONDS);
        }
      }
    }
  }
}
