package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class RequestRouter {
  private final AuthService auth;
  private final Database db;
  private final SessionRegistry registry;

  public RequestRouter(AuthService auth, Database db, SessionRegistry registry) {
    this.auth = auth;
    this.db = db;
    this.registry = registry;
  }

  public Envelope route(ClientSession session, Envelope request) {
    try {
      if (request.version() != 1)
        throw new DomainException(ErrorCode.UNSUPPORTED_VERSION, "Supported version is 1");
      if (request.kind() != Kind.REQUEST
          || request.type() == null
          || request.requestId() == null
          || request.eventId() != null
          || request.ok() != null
          || request.error() != null)
        throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid request envelope");
      try {
        UUID.fromString(request.requestId());
      } catch (IllegalArgumentException e) {
        throw new DomainException(ErrorCode.VALIDATION_ERROR, "requestId must be a UUID");
      }
      RequestType type;
      try {
        type = RequestType.valueOf(request.type());
      } catch (IllegalArgumentException e) {
        throw new DomainException(ErrorCode.VALIDATION_ERROR, "Unknown request type");
      }
      if (type == RequestType.PING) {
        Validation.empty(request.payload());
        return Envelope.success(request, Map.of("message", "PONG"));
      }
      if (type == RequestType.REGISTER_CITIZEN)
        return Envelope.success(
            request, auth.register(Validation.payload(request.payload(), Registration.class)));
      if (type == RequestType.LOGIN) {
        UserView user = auth.login(Validation.payload(request.payload(), Login.class));
        synchronized (registry) {
          registry.login(session, user);
          return Envelope.success(request, user);
        }
      }
      synchronized (registry) {
        registry.requireCurrent(session);
        // Every protected action is authorized from the socket's identity, never a payload role.
        Role role = session.user().role();
        switch (type) {
          case LOGOUT -> {
            Validation.empty(request.payload());
            registry.logout(session);
            return Envelope.success(request, Map.of("message", "Logged out"));
          }
          case WHO_AM_I -> {
            Validation.empty(request.payload());
            return Envelope.success(request, session.user());
          }
          case LIST_TEAMS -> {
            Validation.empty(request.payload());
            requireRole(role, Role.OPERATOR, Role.RESPONDER);
            String own = role == Role.RESPONDER ? session.user().id() : null;
            return Envelope.success(
                request, db.call(c -> new TeamRepository().list(c, own, registry::online)));
          }
          case TEST_EVENT -> {
            Validation.empty(request.payload());
            requireRole(role, Role.OPERATOR);
            // Foundation-only diagnostic: private delivery to the authenticated requesting
            // operator.
            session.send(
                Envelope.event(
                    "TEST_NOTIFICATION", Map.of("message", "Authorized socket event received")));
            return Envelope.success(request, Map.of("message", "Private test event queued"));
          }
          default -> {
            requireRoleForReservedAction(type, role);
            throw new DomainException(
                ErrorCode.INVALID_TRANSITION,
                "This business action belongs to the next implementation stages");
          }
        }
      }
    } catch (DomainException e) {
      return Envelope.failure(request, e.code(), e.getMessage());
    } catch (Exception e) {
      System.err.println("Request failed: " + e.getClass().getSimpleName());
      return Envelope.failure(
          request, ErrorCode.INTERNAL_ERROR, "Server could not process the request");
    }
  }

  private void requireRole(Role actual, Role... allowed) {
    if (Arrays.stream(allowed).noneMatch(r -> r == actual))
      throw new DomainException(ErrorCode.FORBIDDEN, "Action is not permitted for this role");
  }

  private void requireRoleForReservedAction(RequestType type, Role role) {
    switch (type) {
      case CREATE_INCIDENT -> requireRole(role, Role.CITIZEN);
      case SET_PRIORITY, ASSIGN_TEAM, CANCEL_ASSIGNMENT, CLOSE_INCIDENT, REJECT_INCIDENT ->
          requireRole(role, Role.OPERATOR);
      case RESPOND_ASSIGNMENT, UPDATE_ASSIGNMENT_STATUS, SET_TEAM_AVAILABILITY ->
          requireRole(role, Role.RESPONDER);
      default -> {}
    }
  }
}
