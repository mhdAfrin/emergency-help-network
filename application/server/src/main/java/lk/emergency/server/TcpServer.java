package lk.emergency.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

  public TcpServer(String host, int port, int limit, Database db, AuthService auth)
      throws IOException {
    if (limit < 1 || limit > 200)
      throw new IllegalArgumentException("Connection limit must be 1–200");
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
        ClientSession session = new ClientSession(socket, router, registry, watchdog, 5000);
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
    readers.shutdownNow();
    watchdog.shutdownNow();
    try {
      readers.awaitTermination(10, TimeUnit.SECONDS);
      watchdog.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
