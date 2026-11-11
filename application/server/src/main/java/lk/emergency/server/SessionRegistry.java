package lk.emergency.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.UserView;

/** Serializes identity changes with routing, so a replaced socket cannot perform another action. */
public final class SessionRegistry {
  private final Map<String, ClientSession> authenticated = new ConcurrentHashMap<>();

  public synchronized void login(ClientSession session, UserView user) {
    if (session.isClosed())
      throw new DomainException(
          lk.emergency.common.Types.ErrorCode.UNAUTHENTICATED, "Connection closed");
    if (session.user() != null)
      throw new DomainException(
          lk.emergency.common.Types.ErrorCode.INVALID_TRANSITION,
          "Log out before logging in again");
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
    UserView user = session.user();
    if (user != null) authenticated.remove(user.id(), session);
    session.setUser(null);
  }

  public boolean online(String id) {
    return authenticated.containsKey(id);
  }

  public synchronized void requireCurrent(ClientSession session) {
    if (session.user() == null || authenticated.get(session.user().id()) != session)
      throw new DomainException(
          lk.emergency.common.Types.ErrorCode.UNAUTHENTICATED, "Login required");
  }
}
