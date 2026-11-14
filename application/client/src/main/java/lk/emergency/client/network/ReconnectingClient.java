package lk.emergency.client.network;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import lk.emergency.client.recovery.*;
import lk.emergency.common.*;

public final class ReconnectingClient implements Transport {
  private final String host;
  private final int port;
  private final Consumer<Envelope> events;
  private final Consumer<String> status;
  private final NetworkTiming timing;
  private final ScheduledExecutorService supervisor =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "client-reconnect"));
  private volatile ConnectionManager current;
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final AtomicLong generation = new AtomicLong();
  private volatile boolean authenticated;
  private long nextAttempt;
  private int attempt;
  private boolean lossReported;

  public ReconnectingClient(
      String host, int port, Consumer<Envelope> events, Consumer<String> status) {
    this(host, port, events, status, NetworkTiming.DEFAULT);
  }

  public ReconnectingClient(
      String host,
      int port,
      Consumer<Envelope> events,
      Consumer<String> status,
      NetworkTiming timing) {
    this.host = host;
    this.port = port;
    this.events = events;
    this.status = status;
    this.timing = timing;
    connect();
    supervisor.scheduleWithFixedDelay(this::tick, 100, 100, TimeUnit.MILLISECONDS);
  }

  private void connect() {
    long next = generation.incrementAndGet();
    authenticated = false;
    try {
      ConnectionManager replacement =
          new ConnectionManager(
              host,
              port,
              e -> {
                if (generation.get() == next && !stopped.get()) {
                  if (e.type().equals("SESSION_REPLACED")) authenticated = false;
                  events.accept(e);
                }
              },
              timing);
      if (stopped.get()) {
        replacement.close();
        return;
      }
      current = replacement;
      attempt = 0;
      lossReported = false;
      status.accept("Connected to server. Login required.");
    } catch (IOException e) {
      current = null;
      nextAttempt =
          System.nanoTime()
              + TimeUnit.MILLISECONDS.toNanos(
                  ReconnectPolicy.delayMillis(attempt++, ThreadLocalRandom.current().nextDouble()));
    }
  }

  private void tick() {
    if (stopped.get()) return;
    ConnectionManager connection = current;
    if (connection != null && !connection.isClosed()) return;
    authenticated = false;
    if (!lossReported) {
      lossReported = true;
      status.accept(
          "Connection lost. Reconnecting in the background; login again after connection returns.");
      if (nextAttempt == 0)
        nextAttempt =
            System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(
                    ReconnectPolicy.delayMillis(
                        attempt++, ThreadLocalRandom.current().nextDouble()));
    }
    if (System.nanoTime() >= nextAttempt) {
      connect();
      if (current != null) nextAttempt = 0;
    }
  }

  public boolean connected() {
    ConnectionManager connection = current;
    return connection != null && !connection.isClosed();
  }

  public boolean authenticated() {
    return authenticated && connected();
  }

  public long generation() {
    return generation.get();
  }

  private CompletableFuture<Envelope> dispatch(Envelope request, boolean retry) {
    ConnectionManager connection = current;
    long captured = generation.get();
    if (connection == null || connection.isClosed())
      return CompletableFuture.failedFuture(new IOException("Waiting for reconnection"));
    if (!request.type().equals("LOGIN")
        && !request.type().equals("REGISTER_CITIZEN")
        && !request.type().equals("PING")
        && !authenticated)
      return CompletableFuture.failedFuture(
          new IOException("Login required on the new connection"));
    return (retry ? connection.resend(request) : connection.send(request))
        .thenApply(
            response -> {
              if (captured != generation.get())
                throw new CompletionException(new IOException("Connection changed; log in again"));
              if (!Boolean.TRUE.equals(response.ok())
                  && response.error() != null
                  && response.error().code() == lk.emergency.common.Types.ErrorCode.UNAUTHENTICATED)
                authenticated = false;
              if (Boolean.TRUE.equals(response.ok())) {
                if (request.type().equals("LOGIN")) authenticated = true;
                else if (request.type().equals("LOGOUT")) authenticated = false;
              }
              return response;
            });
  }

  public CompletableFuture<Envelope> send(Envelope request) {
    return dispatch(request, false);
  }

  public CompletableFuture<Envelope> resend(Envelope request) {
    return dispatch(request, true);
  }

  public boolean isClosed() {
    return !connected();
  }

  @Override
  public void close() {
    if (!stopped.compareAndSet(false, true)) return;
    authenticated = false;
    generation.incrementAndGet();
    supervisor.shutdownNow();
    ConnectionManager connection = current;
    if (connection != null) connection.close();
    try {
      supervisor.awaitTermination(7, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
