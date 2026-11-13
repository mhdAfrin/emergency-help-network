package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.util.*;
import java.util.function.Consumer;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class RequestRouter {
  private final AuthService auth;
  private final Database db;
  private final SessionRegistry registry;
  private final IncidentService incidents;
  private final AssignmentService assignments;
  private final MutationSupport mutations;
  private final NotificationService notifications;

  public RequestRouter(AuthService auth, Database db, SessionRegistry registry) {
    this.auth = auth;
    this.db = db;
    this.registry = registry;
    incidents = new IncidentService(db);
    assignments = new AssignmentService(db);
    mutations = new MutationSupport(db);
    notifications = new NotificationService(db, registry);
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
        var user = auth.login(Validation.payload(request.payload(), Login.class));
        synchronized (registry) {
          registry.login(session, user);
          return Envelope.success(request, user);
        }
      }
      synchronized (registry) {
        registry.requireCurrent(session);
        Role role = session.user().role();
        String user = session.user().id();
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
            requireRole(role, Role.OPERATOR, Role.RESPONDER);
            var filter = Validation.payload(request.payload(), TeamFilter.class);
            return Envelope.success(
                request,
                db
                    .call(
                        c ->
                            new TeamRepository()
                                .list(c, role == Role.RESPONDER ? user : null, registry::online))
                    .stream()
                    .filter(
                        t ->
                            filter.serviceType() == null || t.serviceType() == filter.serviceType())
                    .filter(
                        t ->
                            filter.availability() == null
                                || t.availability() == filter.availability())
                    .filter(t -> filter.presence() == null || t.presence() == filter.presence())
                    .toList());
          }
          case CREATE_INCIDENT -> {
            requireRole(role, Role.CITIZEN);
            var input = Validation.payload(request.payload(), IncidentInput.class);
            return mutate(
                user,
                request,
                c -> Envelope.success(request, incidents.createIncident(c, user, input)),
                e -> notifications.created(convert(e, IncidentView.class)));
          }
          case GET_INCIDENT -> {
            requireRole(role, Role.CITIZEN, Role.OPERATOR);
            var query = Validation.payload(request.payload(), IncidentQuery.class);
            return Envelope.success(request, incidents.details(user, role, query));
          }
          case LIST_INCIDENTS -> {
            requireRole(role, Role.CITIZEN, Role.OPERATOR);
            var filter = Validation.payload(request.payload(), IncidentFilter.class);
            return Envelope.success(request, incidents.list(user, role, filter));
          }
          case SET_PRIORITY -> {
            requireRole(role, Role.OPERATOR);
            var input = Validation.payload(request.payload(), PriorityUpdateInput.class);
            return mutate(
                user,
                request,
                c -> Envelope.success(request, incidents.updatePriority(c, user, role, input)),
                e -> notifications.incident(convert(e, IncidentView.class)));
          }
          case ASSIGN_TEAM -> {
            requireRole(role, Role.OPERATOR);
            var input = Validation.payload(request.payload(), AssignmentInput.class);
            return mutate(
                user,
                request,
                c ->
                    Envelope.success(
                        request, assignments.assignTeam(c, user, role, input, registry::online)),
                e -> notifications.assignment(convert(e, AssignmentView.class), true));
          }
          case CANCEL_ASSIGNMENT -> {
            requireRole(role, Role.OPERATOR);
            var input = Validation.payload(request.payload(), AssignmentCancellationInput.class);
            return mutate(
                user,
                request,
                c -> Envelope.success(request, assignments.cancelAssignment(c, user, role, input)),
                e -> notifications.assignment(convert(e, AssignmentView.class), false));
          }
          case REJECT_INCIDENT -> {
            requireRole(role, Role.OPERATOR);
            var input = Validation.payload(request.payload(), IncidentRejectionInput.class);
            return mutate(
                user,
                request,
                c -> Envelope.success(request, incidents.rejectIncident(c, user, role, input)),
                e -> notifications.incident(convert(e, IncidentView.class)));
          }
          case SET_TEAM_AVAILABILITY -> {
            requireRole(role, Role.RESPONDER);
            var input = Validation.payload(request.payload(), TeamAvailabilityInput.class);
            return mutate(
                user,
                request,
                c ->
                    Envelope.success(
                        request, assignments.changeAvailability(c, user, input, registry::online)),
                e -> notifications.team(convert(e, TeamView.class)));
          }
          case LIST_ASSIGNMENTS -> {
            requireRole(role, Role.RESPONDER);
            var input = Validation.payload(request.payload(), AssignmentFilter.class);
            return Envelope.success(request, assignments.offers(user, input));
          }
          case TEST_EVENT -> {
            requireRole(role, Role.OPERATOR);
            Validation.empty(request.payload());
            session.send(
                Envelope.event(
                    "TEST_NOTIFICATION", Map.of("message", "Authorized socket event received")));
            return Envelope.success(request, Map.of("message", "Private test event queued"));
          }
          default -> {
            switch (type) {
              case CLOSE_INCIDENT -> requireRole(role, Role.OPERATOR);
              case RESPOND_ASSIGNMENT, UPDATE_ASSIGNMENT_STATUS ->
                  requireRole(role, Role.RESPONDER);
              default -> {}
            }
            throw new DomainException(
                ErrorCode.INVALID_TRANSITION, "This action is reserved for Member 3");
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

  private Envelope mutate(
      String user, Envelope request, Database.Work<Envelope> work, Consumer<Envelope> afterCommit) {
    var result = mutations.execute(user, request, work);
    if (!result.replayed())
      try {
        afterCommit.accept(result.response());
      } catch (RuntimeException e) {
        System.err.println("Committed change notification failed: " + e.getClass().getSimpleName());
      }
    return result.response();
  }

  private <T> T convert(Envelope e, Class<T> type) {
    return Validation.payload(e.payload(), type);
  }

  private void requireRole(Role actual, Role... allowed) {
    if (Arrays.stream(allowed).noneMatch(r -> r == actual))
      throw new DomainException(ErrorCode.FORBIDDEN, "Action not permitted for this role");
  }
}
