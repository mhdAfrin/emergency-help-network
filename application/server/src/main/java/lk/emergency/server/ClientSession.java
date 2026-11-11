package lk.emergency.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.UserView;

public final class ClientSession implements AutoCloseable {
  private final Socket socket;
  private final RequestRouter router;
  private final SessionRegistry registry;
  private final OutboundQueue outgoing = new OutboundQueue();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ScheduledExecutorService watchdog;
  private final long writeTimeoutMillis;
  private volatile UserView user;
  private volatile long writingSince;
  private volatile boolean finishing;
  private Thread writer;

  public ClientSession(
      Socket socket,
      RequestRouter router,
      SessionRegistry registry,
      ScheduledExecutorService watchdog,
      long writeTimeoutMillis) {
    this.socket = socket;
    this.router = router;
    this.registry = registry;
    this.watchdog = watchdog;
    this.writeTimeoutMillis = writeTimeoutMillis;
  }

  public boolean isClosed() {
    return closed.get();
  }

  public UserView user() {
    return user;
  }

  void setUser(UserView user) {
    this.user = user;
  }

  public boolean send(Envelope envelope) {
    if (closed.get() || finishing) return false;
    if (outgoing.offer(envelope)) return true;
    close();
    return false;
  }

  public void finishWith(Envelope last) {
    if (send(last)) {
      finishing = true;
      try {
        socket.shutdownInput();
      } catch (IOException ignored) {
      }
    }
  }

  public void run() {
    ScheduledFuture<?> deadline = null;
    try {
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(60_000);
      writer = new Thread(this::writeLoop, "session-writer");
      writer.start();
      deadline =
          watchdog.scheduleAtFixedRate(
              () -> {
                long since = writingSince;
                if (since != 0
                    && System.nanoTime() - since
                        > TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)) close();
              },
              100,
              100,
              TimeUnit.MILLISECONDS);
      while (!closed.get() && !finishing) {
        Envelope request = FrameCodec.read(socket.getInputStream());
        if (request == null) break;
        send(router.route(this, request));
      }
    } catch (IOException e) {
      /* Invalid frames or dead peers close only this connection; never dump payloads. */
    } finally {
      if (finishing && writer != null)
        try {
          writer.join(writeTimeoutMillis + 200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      close();
      if (deadline != null) deadline.cancel(false);
      if (writer != null && writer != Thread.currentThread())
        try {
          writer.join(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
    }
  }

  private void writeLoop() {
    try {
      while (!closed.get()) {
        Envelope frame = outgoing.take();
        writingSince = System.nanoTime();
        FrameCodec.write(socket.getOutputStream(), frame);
        writingSince = 0;
        if (frame.type().equals("SESSION_REPLACED") || (finishing && outgoing.size() == 0)) {
          close();
          break;
        }
      }
    } catch (IOException e) {
      close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (finishing) close();
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      registry.logout(this);
      if (writer != null) writer.interrupt();
    }
  }
}
