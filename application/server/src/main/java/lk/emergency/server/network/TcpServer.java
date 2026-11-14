package lk.emergency.server.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lk.emergency.server.data.*;
import lk.emergency.server.service.*;

public final class TcpServer implements AutoCloseable {
  private final ServerSocket listener;
  private final Semaphore slots;
  private final ExecutorService readers;
  private final ScheduledExecutorService watchdog =
      Executors.newScheduledThreadPool(1, r -> new Thread(r, "write-watchdog"));
  private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();
  private final SessionRegistry registry = new SessionRegistry();
  private final RequestRouter router;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread acceptor;
  private final lk.emergency.common.NetworkTiming timing;

  public TcpServer(String host, int port, int limit, Database db, AuthService auth)
      throws IOException {
    this(host, port, limit, db, auth, lk.emergency.common.NetworkTiming.DEFAULT);
  }

  public TcpServer(
      String host,
      int port,
      int limit,
      Database db,
      AuthService auth,
      lk.emergency.common.NetworkTiming timing)
      throws IOException {
    this.timing = timing;
    db.transaction(
        c -> {
          new TeamRepository().reconcile(c);
          return null;
        });
    if (limit < 1 || limit > 200)
      throw new IllegalArgumentException("Connection limit must be 1â€“200");
    slots = new Semaphore(limit);
    readers = Executors.newFixedThreadPool(limit, r -> new Thread(r, "session-reader"));
    router = new RequestRouter(auth, db, registry);
    listener = new ServerSocket();
    listener.setReuseAddress(true);
    listener.bind(new InetSocketAddress(host, port), limit);
    acceptor = new Thread(this::acceptLoop, "tcp-acceptor");
    acceptor.start();
  }

  public int port() {
    return listener.getLocalPort();
  }

  public int sessionCount() {
    return sessions.size();
  }

  public void await() throws InterruptedException {
    acceptor.join();
  }

  private void acceptLoop() {
    while (!closed.get()) {
      try {
        Socket socket = listener.accept();
        if (!slots.tryAcquire()) {
          socket.close();
          continue;
        }
        ClientSession session =
            new ClientSession(
                socket,
                router,
                registry,
                watchdog,
                timing.writeTimeoutMillis(),
                timing.inactivityMillis());
        sessions.add(session);
        try {
          readers.execute(
              () -> {
                try {
                  session.run();
                } finally {
                  sessions.remove(session);
                  slots.release();
                }
              });
        } catch (RejectedExecutionException e) {
          sessions.remove(session);
          slots.release();
          session.close();
        }
      } catch (IOException e) {
        if (!closed.get()) System.err.println("Accept failed: " + e.getClass().getSimpleName());
      }
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      listener.close();
    } catch (IOException ignored) {
    }
    try {
      acceptor.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    sessions.forEach(ClientSession::close);
    readers.shutdown();
    watchdog.shutdown();
    try {
      if (!readers.awaitTermination(10, TimeUnit.SECONDS)) readers.shutdownNow();
      if (!watchdog.awaitTermination(2, TimeUnit.SECONDS)) watchdog.shutdownNow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
