package lk.emergency.client.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import lk.emergency.common.Envelope;
import lk.emergency.common.FrameCodec;
import lk.emergency.common.Types.Kind;

/** Checks the application protocol, not merely whether some service opened a TCP port. */
public final class ServerProbe {
  private ServerProbe() {}

  public static double check(String host, int port) throws IOException {
    Envelope request = Envelope.request("PING", UUID.randomUUID().toString(), Map.of());
    long start = System.nanoTime();
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(ClientOptions.validateHost(host), port), 3000);
      socket.setSoTimeout(5000);
      FrameCodec.write(socket.getOutputStream(), request);
      Envelope reply = FrameCodec.read(socket.getInputStream());
      if (reply == null || reply.version() != 1 || reply.kind() != Kind.RESPONSE
          || !request.requestId().equals(reply.requestId()) || !reply.type().equals("PING")
          || !Boolean.TRUE.equals(reply.ok()) || reply.payload() == null
          || !"PONG".equals(reply.payload().path("message").asText()))
        throw new IOException("TCP connected, but the service did not return a valid EHN PONG.");
      return (System.nanoTime() - start) / 1_000_000.0;
    }
  }
}
