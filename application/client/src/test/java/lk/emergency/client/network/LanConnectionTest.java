package lk.emergency.client.network;

import static org.junit.jupiter.api.Assertions.*;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lk.emergency.common.*;
import lk.emergency.server.data.Database;
import lk.emergency.server.network.TcpServer;
import lk.emergency.server.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanConnectionTest {
  @TempDir Path dir;

  @Test void endpointArgumentsAreValidatedAndPromptsRemainOptional() {
    assertNull(ClientOptions.parse(new String[]{}).host());
    var options = ClientOptions.parse(new String[]{"--host", "192.168.1.10", "--port", "5151", "--check"});
    assertEquals("192.168.1.10", options.host()); assertEquals(5151, options.port()); assertTrue(options.check());
    assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse(new String[]{"--host", "0.0.0.0"}));
    assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse(new String[]{"--host", "http://192.168.1.10"}));
    assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse(new String[]{"--port", "65536"}));
    assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse(new String[]{"--check"}));
    assertThrows(IllegalArgumentException.class, () -> ClientOptions.parse(new String[]{"--host"}));
  }

  @Test void wildcardBoundServerSpeaksTheSameProtocolToIndependentClients() throws Exception {
    try (Database db = new Database(dir.resolve("probe.db"));
         TcpServer server = new TcpServer("0.0.0.0", 0, 8, db, new AuthService(db));
         ConnectionManager first = new ConnectionManager("127.0.0.1", server.port(), event -> {});
         ConnectionManager second = new ConnectionManager("127.0.0.1", server.port(), event -> {})) {
      assertEquals("PONG", first.call("PING", Map.of()).payload().path("message").asText());
      assertEquals("PONG", second.call("PING", Map.of()).payload().path("message").asText());
      assertTrue(ServerProbe.check("127.0.0.1", server.port()) >= 0);
    }
  }

  @Test void probeRejectsAServiceThatOnlyOpensATcpPort() throws Exception {
    try (ServerSocket fake = new ServerSocket(0)) {
      var task = CompletableFuture.runAsync(() -> {
        try (var socket = fake.accept()) {
          Envelope request = FrameCodec.read(socket.getInputStream());
          FrameCodec.write(socket.getOutputStream(), Envelope.success(request, Map.of("message", "WRONG_SERVICE")));
        } catch (Exception failure) { throw new RuntimeException(failure); }
      });
      assertThrows(java.io.IOException.class, () -> ServerProbe.check("127.0.0.1", fake.getLocalPort()));
      task.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
  }
}
