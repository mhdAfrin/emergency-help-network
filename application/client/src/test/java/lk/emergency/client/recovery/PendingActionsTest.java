package lk.emergency.client.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.client.network.*;
import lk.emergency.common.*;
import org.junit.jupiter.api.Test;

class PendingActionsTest {
  @Test
  void timeoutRetainsInputAndLateResponseCanBeReconciled() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      CountDownLatch release = new CountDownLatch(1);
      Future<?> peer =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  Envelope request = FrameCodec.read(socket.getInputStream());
                  release.await(3, TimeUnit.SECONDS);
                  FrameCodec.write(
                      socket.getOutputStream(),
                      Envelope.success(request, Map.of("id", "confirmed-reference")));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (ConnectionManager connection =
          new ConnectionManager("127.0.0.1", server.getLocalPort(), e -> {})) {
        PendingActions pending = new PendingActions(connection);
        Map<String, String> input = Map.of("description", "Retain my fictional report");
        assertThrows(
            TimeoutException.class,
            () -> pending.submit("owner", "CREATE_INCIDENT", input, Duration.ofMillis(50)));
        var ticket = pending.list("owner").getFirst();
        assertEquals(Json.tree(input), ticket.request().payload());
        assertTrue(pending.list("other").isEmpty());
        assertThrows(
            IOException.class,
            () ->
                pending.resolve(
                    "other", ticket.request().requestId(), false, Duration.ofMillis(10)));
        release.countDown();
        assertEquals(
            "confirmed-reference",
            pending
                .resolve("owner", ticket.request().requestId(), false, Duration.ofSeconds(3))
                .payload()
                .get("id")
                .asText());
        assertTrue(pending.list("owner").isEmpty());
      } finally {
        release.countDown();
        peer.get(5, TimeUnit.SECONDS);
        worker.shutdownNow();
      }
    }
  }

  @Test
  void retryUsesSameRequestIdAndUnchangedPayload() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      Future<?> peer =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  Envelope first = FrameCodec.read(socket.getInputStream()),
                      retry = FrameCodec.read(socket.getInputStream());
                  assertEquals(first, retry);
                  FrameCodec.write(
                      socket.getOutputStream(), Envelope.success(first, Map.of("saved", true)));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (ConnectionManager connection =
          new ConnectionManager("127.0.0.1", server.getLocalPort(), e -> {})) {
        PendingActions pending = new PendingActions(connection);
        assertThrows(
            TimeoutException.class,
            () ->
                pending.submit(
                    "owner",
                    "CREATE_INCIDENT",
                    Map.of("description", "Original"),
                    Duration.ofMillis(50)));
        String id = pending.list("owner").getFirst().request().requestId();
        assertTrue(pending.resolve("owner", id, true, Duration.ofSeconds(3)).ok());
      } finally {
        peer.get(5, TimeUnit.SECONDS);
        worker.shutdownNow();
      }
    }
  }

  @Test
  void disconnectRetainsUnknownOutcomeAndOriginalInput() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      Future<?> peer =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  assertNotNull(FrameCodec.read(socket.getInputStream()));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (ConnectionManager connection =
          new ConnectionManager("127.0.0.1", server.getLocalPort(), e -> {})) {
        PendingActions pending = new PendingActions(connection);
        assertThrows(
            IOException.class,
            () ->
                pending.submit(
                    "owner",
                    "CREATE_INCIDENT",
                    Map.of("description", "Keep input"),
                    Duration.ofSeconds(3)));
        assertEquals(
            "Keep input",
            pending.list("owner").getFirst().request().payload().get("description").asText());
      } finally {
        peer.get(5, TimeUnit.SECONDS);
        worker.shutdownNow();
      }
    }
  }
}
