package lk.emergency.server.network;

import static lk.emergency.common.Types.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.service.*;

/**
 * The router monitor orders business mutations, identity changes, snapshot watermarks and events.
 */
public final class SessionRegistry {
  private final Map<String, ClientSession> authenticated = new ConcurrentHashMap<>();
  private final String epoch = UUID.randomUUID().toString();
  private volatile long sequence;
  private Consumer<String> presence = user -> {};

  public synchronized void presenceListener(Consumer<String> listener) {
    presence = listener;
  }

  public String epoch() {
    return epoch;
  }

  public long sequence() {
    return sequence;
  }

  public synchronized Envelope event(String type, Object data) {
    Envelope e = Envelope.event(type, data);
    return new Envelope(
        e.version(),
        e.kind(),
        e.type(),
        e.requestId(),
        e.eventId(),
        e.payload(),
        e.ok(),
        e.error(),
        epoch,
        ++sequence);
  }

  public synchronized void login(ClientSession session, UserView user) {
    if (session.isClosed())
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Connection closed");
    if (session.user() != null)
      throw new DomainException(ErrorCode.INVALID_TRANSITION, "Log out before logging in again");
    ClientSession old = authenticated.put(user.id(), session);
    session.setUser(user);
    session.clearSnapshot();
    if (old != null && old != session) {
      old.setUser(null);
      old.finishWith(
          Envelope.event(
              "SESSION_REPLACED", Map.of("message", "A new login replaced this session")));
    }
    notifyPresence(user.id());
  }

  public synchronized void logout(ClientSession session) {
    var user = session.user();
    boolean removed = user != null && authenticated.remove(user.id(), session);
    session.setUser(null);
    session.clearSnapshot();
    if (removed) notifyPresence(user.id());
  }

  private void notifyPresence(String id) {
    try {
      presence.accept(id);
    } catch (RuntimeException e) {
      System.err.println("Presence notification unavailable");
    }
  }

  public boolean online(String id) {
    var session = authenticated.get(id);
    return session != null && !session.isClosed();
  }

  public synchronized void requireCurrent(ClientSession session) {
    if (session.user() == null
        || session.isClosed()
        || authenticated.get(session.user().id()) != session)
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Login required");
  }

  public synchronized void operators(Envelope event) {
    for (var session : List.copyOf(authenticated.values())) {
      var user = session.user();
      if (user != null && user.role() == Role.OPERATOR) session.send(event);
    }
  }

  public synchronized void user(String id, Envelope event) {
    var session = authenticated.get(id);
    if (session != null) session.send(event);
  }
}
