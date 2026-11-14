package lk.emergency.client;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.common.*;

/** Keeps the exact business request/input while its outcome is uncertain. No credentials. */
public final class PendingActions {
  public record Ticket(String owner, Envelope request, CompletableFuture<Envelope> response) {}

  private final ConnectionManager connection;
  private final Map<String, Ticket> tickets = new LinkedHashMap<>();

  public PendingActions(ConnectionManager connection) {
    this.connection = connection;
  }

  public Envelope submit(String owner, String type, Object payload)
      throws IOException, TimeoutException {
    return submit(owner, type, payload, Duration.ofSeconds(10));
  }

  // An injectable wait lets tests exercise timeout/late response without waiting ten seconds.
  public Envelope submit(String owner, String type, Object payload, Duration wait)
      throws IOException, TimeoutException {
    if (tickets.size() >= 20)
      throw new IOException("Resolve pending actions before submitting more");
    Envelope request = Envelope.request(type, UUID.randomUUID().toString(), payload);
    Ticket ticket = new Ticket(owner, request, connection.send(request));
    tickets.put(request.requestId(), ticket);
    return await(ticket, wait);
  }

  public List<Ticket> list(String owner) {
    return tickets.values().stream().filter(t -> t.owner().equals(owner)).toList();
  }

  public Envelope resolve(String owner, String id, boolean retry, Duration wait)
      throws IOException, TimeoutException {
    Ticket ticket = tickets.get(id);
    if (ticket == null || !ticket.owner().equals(owner))
      throw new IOException("No pending action for this user and ID");
    if (retry && !ticket.response().isDone())
      try {
        connection.retry(id);
      } catch (IOException e) {
        if (!ticket.response().isDone()) throw e;
      }
    return await(ticket, wait);
  }

  private Envelope await(Ticket ticket, Duration wait) throws IOException, TimeoutException {
    try {
      Envelope result = ticket.response().get(wait.toMillis(), TimeUnit.MILLISECONDS);
      tickets.remove(ticket.request().requestId());
      return result;
    } catch (TimeoutException e) {
      throw new TimeoutException(
          "Confirmation pending. Input retained; request ID: "
              + ticket.request().requestId()
              + ". Use Pending actions to inspect or retry this exact request.");
    } catch (ExecutionException e) {
      throw new IOException(
          "Outcome unresolved after connection loss; input retained for this user during this"
              + " client session",
          e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted; request retained", e);
    }
  }
}
