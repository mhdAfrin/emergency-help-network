package lk.emergency.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.client.recovery.*;
import lk.emergency.common.*;
import org.junit.jupiter.api.Test;

class ConnectionManagerTest {
  @Test
  void backgroundEventsAndOutOfOrderCorrelation() throws Exception {
    try (ServerSocket listener = new ServerSocket(0)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      BlockingQueue<Envelope> events = new LinkedBlockingQueue<>();
      Future<?> server =
          executor.submit(
              () -> {
                try (Socket socket = listener.accept()) {
                  Envelope a = FrameCodec.read(socket.getInputStream()),
                      b = FrameCodec.read(socket.getInputStream());
                  FrameCodec.write(
                      socket.getOutputStream(),
                      Envelope.event("TEST_NOTIFICATION", Map.of("hello", "world")));
                  FrameCodec.write(
                      socket.getOutputStream(), Envelope.success(b, Map.of("order", 2)));
                  FrameCodec.write(
                      socket.getOutputStream(), Envelope.success(a, Map.of("order", 1)));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (ConnectionManager client =
          new ConnectionManager("127.0.0.1", listener.getLocalPort(), events::add)) {
        var first = client.send(Envelope.request("PING", UUID.randomUUID().toString(), Map.of()));
        var second = client.send(Envelope.request("PING", UUID.randomUUID().toString(), Map.of()));
        assertEquals(2, second.get(5, TimeUnit.SECONDS).payload().get("order").asInt());
        assertEquals(1, first.get(5, TimeUnit.SECONDS).payload().get("order").asInt());
        assertNotNull(events.poll(2, TimeUnit.SECONDS));
      } finally {
        server.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();
      }
    }
  }
}
