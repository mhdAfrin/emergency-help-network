package lk.emergency.client;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;
import org.junit.jupiter.api.Test;

class HeartbeatTest {
  @Test
  void productionIntervalsAreFifteenAndFortyFiveSeconds() {
    assertEquals(15000, NetworkTiming.DEFAULT.heartbeatMillis());
    assertEquals(45000, NetworkTiming.DEFAULT.inactivityMillis());
  }

  @Test
  void backgroundHeartbeatsWorkWithoutMenusAndLostInboundClosesConnection() throws Exception {
    try (ServerSocket listener = new ServerSocket(0)) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      CountDownLatch healthy = new CountDownLatch(1);
      Queue<String> ids = new ConcurrentLinkedQueue<>();
      Future<?> server =
          worker.submit(
              () -> {
                try (var socket = listener.accept()) {
                  socket.setSoTimeout(3000);
                  for (int i = 0; i < 5; i++) {
                    Envelope ping = FrameCodec.read(socket.getInputStream());
                    assertEquals("PING", ping.type());
                    ids.add(ping.requestId());
                    FrameCodec.write(
                        socket.getOutputStream(),
                        Envelope.success(ping, Map.of("message", "PONG")));
                  }
                  healthy.countDown();
                  while (FrameCodec.read(socket.getInputStream()) != null) {}
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (ConnectionManager connection =
          new ConnectionManager(
              "127.0.0.1", listener.getLocalPort(), e -> {}, new NetworkTiming(60, 600, 1000))) {
        assertTrue(healthy.await(3, TimeUnit.SECONDS));
        assertFalse(connection.isClosed());
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!connection.isClosed() && System.nanoTime() < end) Thread.sleep(20);
        assertTrue(connection.isClosed());
        assertEquals(5, new HashSet<>(ids).size());
      } finally {
        server.get(5, TimeUnit.SECONDS);
        worker.shutdownNow();
      }
    }
  }
}
