package lk.emergency.client;

import static lk.emergency.common.Types.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lk.emergency.common.*;

/** One reader, one writer. Pending IDs survive timeout until response or disconnect. */
public final class ConnectionManager implements Transport {
  private final Socket socket = new Socket();
  private final OutboundQueue outgoing = new OutboundQueue();
  private final Map<String, CompletableFuture<Envelope>> pending = new ConcurrentHashMap<>();
  private final Map<String, Envelope> pendingRequests = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread reader, writer;
  private final Consumer<Envelope> events;
  private final NetworkTiming timing;
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "client-heartbeat"));
  private volatile long lastInbound = System.nanoTime(),
      lastHeartbeat = System.nanoTime(),
      writingSince;

  public ConnectionManager(String host, int port, Consumer<Envelope> events) throws IOException {
    this(host, port, events, NetworkTiming.DEFAULT);
  }

  public ConnectionManager(String host, int port, Consumer<Envelope> events, NetworkTiming timing)
      throws IOException {
    this.events = events;
    this.timing = timing;
    try {
      socket.connect(new InetSocketAddress(host, port), 5000);
    } catch (IOException e) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      heartbeat.shutdownNow();
      throw e;
    }
    socket.setTcpNoDelay(true);
    reader = new Thread(this::readLoop, "client-reader");
    writer = new Thread(this::writeLoop, "client-writer");
    reader.start();
    writer.start();
    heartbeat.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
  }

  public CompletableFuture<Envelope> send(Envelope request) {
    if (closed.get()) return CompletableFuture.failedFuture(new IOException("Disconnected"));
    if (pending.size() >= 100)
      return CompletableFuture.failedFuture(new IOException("Too many pending requests"));
    CompletableFuture<Envelope> future = new CompletableFuture<>();
    if (pending.putIfAbsent(request.requestId(), future) != null)
      return pending.get(request.requestId());
    pendingRequests.put(request.requestId(), request);
    if (!outgoing.offer(request)) {
      pending.remove(request.requestId());
      pendingRequests.remove(request.requestId());
      future.completeExceptionally(new IOException("Outbound queue full"));
      close();
    }
    if (closed.get()) {
      pending.remove(request.requestId());
      pendingRequests.remove(request.requestId());
      future.completeExceptionally(new IOException("Disconnected"));
    }
    return future;
  }

  public Envelope call(String type, Object payload) throws IOException, TimeoutException {
    Envelope request = Envelope.request(type, UUID.randomUUID().toString(), payload);
    try {
      return send(request).get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new TimeoutException(
          "Confirmation pending; retained request ID: " + request.requestId());
    } catch (ExecutionException e) {
      throw new IOException("Connection failed", e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }

  public Set<String> pendingIds() {
    return Set.copyOf(pending.keySet());
  }

  public void retry(String id) throws IOException {
    Envelope request = pendingRequests.get(id);
    if (request == null) throw new IOException("No pending request with that ID");
    if (!outgoing.offer(request)) {
      close();
      throw new IOException("Outbound queue full");
    }
  }

  private void readLoop() {
    try {
      while (!closed.get()) {
        Envelope frame = FrameCodec.read(socket.getInputStream());
        if (frame == null) break;
        lastInbound = System.nanoTime();
        if (frame.version() != 1) throw new IOException("Unsupported server version");
        if (frame.kind() == Kind.RESPONSE && frame.requestId() != null) {
          CompletableFuture<Envelope> waiting = pending.remove(frame.requestId());
          pendingRequests.remove(frame.requestId());
          if (waiting != null) waiting.complete(frame);
        } else if (frame.kind() == Kind.EVENT) events.accept(frame);
        else throw new IOException("Unexpected server envelope");
      }
    } catch (IOException e) {
      /* Console reports disconnection without sensitive messages. */
    } finally {
      close();
    }
  }

  private void writeLoop() {
    try {
      while (!closed.get()) {
        Envelope frame = outgoing.take();
        writingSince = System.nanoTime();
        FrameCodec.write(socket.getOutputStream(), frame);
        writingSince = 0;
      }
    } catch (IOException e) {
      close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    pending.values().forEach(f -> f.completeExceptionally(new IOException("Disconnected")));
    pending.clear();
    pendingRequests.clear();
    heartbeat.shutdownNow();
    reader.interrupt();
    writer.interrupt();
    for (Thread thread : List.of(reader, writer))
      if (thread != Thread.currentThread())
        try {
          thread.join(1500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
  }

  @Override
  public CompletableFuture<Envelope> resend(Envelope request) {
    CompletableFuture<Envelope> future = pending.get(request.requestId());
    if (future == null) return send(request);
    if (!outgoing.offer(request)) close();
    return future;
  }

  private void tick() {
    if (closed.get()) return;
    long now = System.nanoTime();
    if (now - lastInbound > TimeUnit.MILLISECONDS.toNanos(timing.inactivityMillis())
        || (writingSince != 0
            && now - writingSince > TimeUnit.MILLISECONDS.toNanos(timing.writeTimeoutMillis()))) {
      close();
      return;
    }
    if (now - lastHeartbeat >= TimeUnit.MILLISECONDS.toNanos(timing.heartbeatMillis())) {
      lastHeartbeat = now;
      send(Envelope.request("PING", UUID.randomUUID().toString(), Map.of()));
    }
  }
}
