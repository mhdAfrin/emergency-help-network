package lk.emergency.client.recovery;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lk.emergency.client.network.*;
import lk.emergency.common.*;

/** Journal before sending. Re-login can resend the original envelope without inventing a new ID. */
public final class PendingActions {
  public record Ticket(String owner, Envelope request, CompletableFuture<Envelope> response) {}

  private final Transport connection;
  private final LocalJournal journal;
  private final Map<String, Ticket> tickets = new LinkedHashMap<>();

  public PendingActions(Transport connection) {
    this(connection, null);
  }

  public PendingActions(Transport connection, LocalJournal journal) {
    this.connection = connection;
    this.journal = journal;
  }

  public Envelope submit(String owner, String type, Object payload)
      throws IOException, TimeoutException {
    return submit(owner, type, payload, Duration.ofSeconds(10));
  }

  public Envelope submit(String owner, String type, Object payload, Duration wait)
      throws IOException, TimeoutException {
    list(owner);
    if (tickets.size() >= 20)
      throw new IOException("Resolve pending actions before submitting more");
    Envelope request = Envelope.request(type, UUID.randomUUID().toString(), payload);
    if (journal != null) {
      journal.put(owner, request);
      if (type.equals("CREATE_INCIDENT")) journal.clearDraft(owner);
    }
    Ticket ticket = new Ticket(owner, request, connection.send(request));
    tickets.put(request.requestId(), ticket);
    return await(ticket, wait);
  }

  public List<Ticket> list(String owner) throws IOException {
    if (journal != null) {
      tickets.entrySet().removeIf(e -> !e.getValue().owner().equals(owner));
      for (var saved : journal.list(owner))
        tickets.putIfAbsent(
            saved.request().requestId(),
            new Ticket(owner, saved.request(), new CompletableFuture<>()));
    }
    return tickets.values().stream().filter(t -> t.owner().equals(owner)).toList();
  }

  public Envelope resolve(String owner, String id, boolean retry, Duration wait)
      throws IOException, TimeoutException {
    list(owner);
    Ticket ticket = tickets.get(id);
    if (ticket == null || !ticket.owner().equals(owner))
      throw new IOException("No pending action for this user and ID");
    if (retry && (!ticket.response().isDone() || ticket.response().isCompletedExceptionally())) {
      ticket = new Ticket(owner, ticket.request(), connection.resend(ticket.request()));
      tickets.put(id, ticket);
    }
    return await(ticket, wait);
  }

  private Envelope await(Ticket ticket, Duration wait) throws IOException, TimeoutException {
    try {
      Envelope result = ticket.response().get(wait.toMillis(), TimeUnit.MILLISECONDS);
      if (journal != null) journal.remove(ticket.owner(), ticket.request().requestId());
      tickets.remove(ticket.request().requestId());
      return result;
    } catch (TimeoutException e) {
      throw new TimeoutException(
          "Confirmation pending. Open Account / Saved actions after login to check or retry the"
              + " saved action.");
    } catch (ExecutionException e) {
      throw new IOException(
          "Outcome uncertain after connection loss; input and original ID retained for"
              + " reconciliation",
          e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted; pending request retained", e);
    }
  }
}
