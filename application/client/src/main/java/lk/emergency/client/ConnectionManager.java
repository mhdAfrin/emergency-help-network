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
public final class ConnectionManager implements AutoCloseable {
  private final Socket socket = new Socket();
  private final OutboundQueue outgoing = new OutboundQueue();
  private final Map<String, CompletableFuture<Envelope>> pending = new ConcurrentHashMap<>();
  private final Map<String, Envelope> pendingRequests = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread reader, writer;
  private final Consumer<Envelope> events;

  public ConnectionManager(String host, int port, Consumer<Envelope> events) throws IOException {
    this.events = events;
    socket.connect(new InetSocketAddress(host, port), 5000);
    socket.setTcpNoDelay(true);
    reader = new Thread(this::readLoop, "client-reader");
    writer = new Thread(this::writeLoop, "client-writer");
    reader.start();
    writer.start();
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
      while (!closed.get()) FrameCodec.write(socket.getOutputStream(), outgoing.take());
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
}
