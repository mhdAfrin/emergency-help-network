package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.UserView;

/** The router and identity changes share this monitor; SQL never waits for this monitor. */
public final class SessionRegistry {
  private final Map<String, ClientSession> authenticated = new ConcurrentHashMap<>();

  public synchronized void login(ClientSession session, UserView user) {
    if (session.isClosed())
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "Connection closed");
    if (session.user() != null)
      throw new DomainException(ErrorCode.INVALID_TRANSITION, "Log out before logging in again");
    ClientSession old = authenticated.put(user.id(), session);
    session.setUser(user);
    if (old != null && old != session) {
      old.setUser(null);
      old.finishWith(
          Envelope.event(
              "SESSION_REPLACED", Map.of("message", "A new login replaced this session")));
    }
  }

  public synchronized void logout(ClientSession session) {
    var user = session.user();
    if (user != null) authenticated.remove(user.id(), session);
    session.setUser(null);
  }

  // Read from DB callbacks without acquiring the registry monitor (avoids a deadlock).
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
