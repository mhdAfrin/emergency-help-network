package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;
import org.junit.jupiter.api.Test;

class SlowWriterTest {
  static class BlockedSocket extends Socket {
    final CountDownLatch writing = new CountDownLatch(1), closed = new CountDownLatch(1);
    final ByteArrayInputStream input;

    BlockedSocket() throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      FrameCodec.write(bytes, Envelope.request("PING", UUID.randomUUID().toString(), Map.of()));
      input = new ByteArrayInputStream(bytes.toByteArray());
    }

    @Override
    public InputStream getInputStream() {
      return new InputStream() {
        @Override
        public int read() throws IOException {
          if (input.available() > 0) return input.read();
          try {
            closed.await();
            return -1;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
          }
        }
      };
    }

    @Override
    public OutputStream getOutputStream() {
      return new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          writing.countDown();
          try {
            closed.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IOException("Closed blocked writer");
        }
      };
    }

    @Override
    public synchronized void close() {
      closed.countDown();
    }
  }

  @Test
  void blockedWriterDeadlineClosesOnlyItsSession() throws Exception {
    ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    BlockedSocket socket = new BlockedSocket();
    SessionRegistry registry = new SessionRegistry();
    // PING does not use auth/database; block the actual writer deterministically.
    ClientSession session =
        new ClientSession(socket, new RequestRouter(null, null, registry), registry, watchdog, 100);
    Thread runner = new Thread(session::run);
    runner.start();
    try {
      assertTrue(socket.writing.await(2, TimeUnit.SECONDS));
      assertTrue(socket.closed.await(3, TimeUnit.SECONDS));
      runner.join(2000);
      assertFalse(runner.isAlive());
    } finally {
      session.close();
      watchdog.shutdownNow();
    }
  }

  @Test
  void fullSessionQueueDisconnectsInsteadOfBlocking() throws Exception {
    ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    BlockedSocket socket = new BlockedSocket();
    SessionRegistry registry = new SessionRegistry();
    ClientSession session =
        new ClientSession(
            socket, new RequestRouter(null, null, registry), registry, watchdog, 5000);
    try {
      for (int i = 0; i < 100; i++)
        assertTrue(session.send(Envelope.event("TEST_NOTIFICATION", Map.of())));
      assertFalse(session.send(Envelope.event("TEST_NOTIFICATION", Map.of())));
      assertTrue(session.isClosed());
    } finally {
      session.close();
      watchdog.shutdownNow();
    }
  }
}
