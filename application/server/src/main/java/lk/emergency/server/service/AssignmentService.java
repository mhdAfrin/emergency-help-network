package lk.emergency.server.service;

import static lk.emergency.common.Types.*;
import static lk.emergency.common.Types.ErrorCode.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.network.*;

public final class AssignmentService {
  private final Database db;
  private final IncidentRepository incidents = new IncidentRepository();
  private final AssignmentRepository assignments = new AssignmentRepository();
  private final TeamRepository teams = new TeamRepository();

  public AssignmentService(Database db) {
    this.db = db;
  }

  public AssignmentView assignTeam(
      Connection c, String actor, Role role, AssignmentInput input, Predicate<String> online)
      throws Exception {
    IncidentService.requireOperator(role);
    long incidentRevision = Validation.revision(input.expectedIncidentRevision()),
        teamRevision = Validation.revision(input.expectedTeamRevision());
    var incident = new IncidentService(db).mutable(c, input.incidentId(), incidentRevision);
    var team = team(c, input.teamId());
    if (!online.test(team.responderUserId()) || team.availability() != Availability.AVAILABLE)
      throw new DomainException(TEAM_UNAVAILABLE, "Team must be online and available");
    if (team.revision() != teamRevision)
      throw new DomainException(
          VERSION_CONFLICT, "Team changed; refresh and review before retrying");
    if (assignments.teamActive(c, team.id())
        || assignments.serviceActive(c, incident.id(), team.serviceType()))
      throw new DomainException(
          ACTIVE_ASSIGNMENT_EXISTS, "An active assignment already reserves this team or service");
    String id = ReferenceIds.next(c), now = Instant.now().toString();
    try {
      assignments.create(c, id, incident.id(), team.id(), team.serviceType(), actor, now);
    } catch (SQLException e) {
      if (e.getErrorCode() == 19
          && e.getMessage().contains("UNIQUE constraint failed: assignments."))
        throw new DomainException(ACTIVE_ASSIGNMENT_EXISTS, "Another active assignment exists");
      throw e;
    }
    teams.availability(c, team, Availability.BUSY);
    incidents.activateOrTouch(c, incident, now);
    new IncidentService(db)
        .event(
            c,
            incident.id(),
            id,
            actor,
            "TEAM_ASSIGNED",
            team.serviceType() + " team offered assistance",
            null,
            now);
    return assignments.findById(c, id);
  }

  public AssignmentView cancelAssignment(
      Connection c, String actor, Role role, AssignmentCancellationInput input) throws Exception {
    IncidentService.requireOperator(role);
    long expected = Validation.revision(input.expectedRevision());
    long incidentRevision = Validation.revision(input.expectedIncidentRevision()),
        teamRevision = Validation.revision(input.expectedTeamRevision());
    String reason = Validation.text(input.reason(), 1, 500, "Cancellation reason");
    var assignment = assignments.findById(c, IncidentService.requiredId(input.assignmentId()));
    if (assignment == null) throw new DomainException(NOT_FOUND, "Assignment not found");
    if (assignment.revision() != expected)
      throw new DomainException(
          VERSION_CONFLICT, "Assignment changed; refresh and review before retrying");
    if (assignment.state() != AssignmentState.OFFERED
        && assignment.state() != AssignmentState.ACCEPTED)
      throw new DomainException(
          INVALID_TRANSITION, "Only offered or accepted assignments can be cancelled");
    var incident = new IncidentService(db).mutable(c, assignment.incidentId(), incidentRevision);
    var team = team(c, assignment.teamId());
    if (team.revision() != teamRevision)
      throw new DomainException(
          VERSION_CONFLICT, "Team changed; refresh and review before retrying");
    if (team.availability() != Availability.BUSY)
      throw new DomainException(
          INVALID_TRANSITION, "Active assignment must retain its busy reservation");
    String now = Instant.now().toString();
    assignments.cancel(c, assignment, reason, now);
    teams.availability(c, team, Availability.AVAILABLE);
    incidents.activateOrTouch(c, incident, now);
    new IncidentService(db)
        .event(
            c,
            incident.id(),
            assignment.id(),
            actor,
            "ASSIGNMENT_CANCELLED",
            assignment.serviceType() + " assignment cancelled",
            reason,
            now);
    return assignments.findById(c, assignment.id());
  }

  public TeamView changeAvailability(
      Connection c, String user, TeamAvailabilityInput input, Predicate<String> online)
      throws Exception {
    long expected = Validation.revision(input.expectedRevision());
    if (input.availability() != Availability.AVAILABLE
        && input.availability() != Availability.UNAVAILABLE)
      throw new DomainException(VALIDATION_ERROR, "Select AVAILABLE or UNAVAILABLE");
    var team = teams.forUser(c, user);
    if (team == null) throw new DomainException(NOT_FOUND, "Own team not found");
    if (assignments.teamActive(c, team.id()) || team.availability() == Availability.BUSY)
      throw new DomainException(
          ACTIVE_ASSIGNMENT_EXISTS, "An active assignment reserves this team");
    if (team.revision() != expected)
      throw new DomainException(
          VERSION_CONFLICT, "Team changed; refresh and review before retrying");
    teams.availability(c, team, input.availability());
    return teams.find(c, team.id()).view(online);
  }

  public List<AssignmentOffer> offers(String user, AssignmentFilter filter) {
    int offset = Validation.offset(filter.offset()), limit = Validation.limit(filter.limit());
    return db.call(
        c -> {
          var team = teams.forUser(c, user);
          if (team == null) throw new DomainException(NOT_FOUND, "Own team not found");
          List<AssignmentOffer> result = new ArrayList<>();
          for (var offer :
              (Boolean.TRUE.equals(filter.history())
                  ? assignments.teamHistory(c, team.id(), offset, limit)
                  : assignments.teamOffers(c, team.id(), offset, limit)))
            result.add(
                new AssignmentOffer(
                    offer,
                    IncidentService.view(incidents.findById(c, offer.incidentId())),
                    offer.state() == AssignmentState.CANCELLED
                        ? null
                        : assignments.reason(c, offer.id())));
          return result;
        });
  }

  private TeamRepository.Team team(Connection c, String id) throws SQLException {
    var team = teams.find(c, IncidentService.requiredId(id));
    if (team == null) throw new DomainException(NOT_FOUND, "Team not found");
    return team;
  }

  public AssignmentView respond(Connection c, String actor, AssignmentResponseInput input)
      throws Exception {
    if (input.decision() == null)
      throw new DomainException(VALIDATION_ERROR, "Choose ACCEPT or DECLINE");
    String reason =
        input.decision() == AssignmentDecision.DECLINE
            ? Validation.text(input.reason(), 1, 500, "Private decline reason")
            : Validation.text(input.reason(), 0, 500, "Private note");
    return responderTransition(
        c,
        actor,
        input.assignmentId(),
        input.expectedRevision(),
        input.expectedIncidentRevision(),
        input.expectedTeamRevision(),
        input.decision() == AssignmentDecision.ACCEPT
            ? AssignmentState.ACCEPTED
            : AssignmentState.DECLINED,
        reason,
        true);
  }

  public AssignmentView progress(Connection c, String actor, AssignmentStatusInput input)
      throws Exception {
    String note = Validation.text(input.note(), 0, 500, "Private note");
    return responderTransition(
        c,
        actor,
        input.assignmentId(),
        input.expectedRevision(),
        input.expectedIncidentRevision(),
        input.expectedTeamRevision(),
        input.status(),
        note,
        false);
  }

  private AssignmentView responderTransition(
      Connection c,
      String actor,
      String id,
      Long expected,
      Long incidentRevision,
      Long teamRevision,
      AssignmentState next,
      String note,
      boolean response)
      throws Exception {
    long revision = Validation.revision(expected),
        ir = Validation.revision(incidentRevision),
        tr = Validation.revision(teamRevision);
    var current = assignments.findById(c, IncidentService.requiredId(id));
    var own = teams.forUser(c, actor);
    if (current == null || own == null || !current.teamId().equals(own.id()))
      throw new DomainException(NOT_FOUND, "Own assignment not found");
    if (current.revision() != revision || own.revision() != tr)
      throw new DomainException(VERSION_CONFLICT, "Assignment/team changed; refresh and review");
    var incident = new IncidentService(db).mutable(c, current.incidentId(), ir);
    boolean allowed =
        response
            ? current.state() == AssignmentState.OFFERED
                && (next == AssignmentState.ACCEPTED || next == AssignmentState.DECLINED)
            : (current.state() == AssignmentState.ACCEPTED && next == AssignmentState.EN_ROUTE)
                || (current.state() == AssignmentState.EN_ROUTE && next == AssignmentState.ARRIVED)
                || (current.state() == AssignmentState.ARRIVED
                    && next == AssignmentState.COMPLETED);
    if (!allowed)
      throw new DomainException(
          INVALID_TRANSITION, "Only the valid next assignment transition is allowed");
    if (own.availability() != Availability.BUSY)
      throw new DomainException(
          INVALID_TRANSITION, "Active assignment must retain its BUSY reservation");
    String now = Instant.now().toString();
    assignments.transition(c, current, next, note.isBlank() ? null : note, now);
    if (next == AssignmentState.DECLINED || next == AssignmentState.COMPLETED)
      teams.availability(c, own, Availability.AVAILABLE);
    incidents.activateOrTouch(c, incident, now);
    new IncidentService(db)
        .event(
            c,
            incident.id(),
            current.id(),
            actor,
            "ASSIGNMENT_" + next,
            current.serviceType() + " assignment " + next,
            note.isBlank() ? null : note,
            now);
    return assignments.findById(c, current.id());
  }

  public AssignmentDetails details(String user, AssignmentQuery query) {
    int offset = Validation.offset(query.offset());
    return db.call(
        c -> {
          var team = teams.forUser(c, user);
          var assignment = assignments.findById(c, IncidentService.requiredId(query.id()));
          if (team == null || assignment == null || !assignment.teamId().equals(team.id()))
            throw new DomainException(NOT_FOUND, "Own assignment not found");
          List<AssignmentTimelineEvent> events = new ArrayList<>();
          try (var p =
              c.prepareStatement(
                  "SELECT id,event_type,public_message,actor_user_id,internal_note,created_at FROM"
                      + " incident_events WHERE assignment_id=? ORDER BY created_at,id LIMIT 11"
                      + " OFFSET ?")) {
            p.setString(1, assignment.id());
            p.setInt(2, offset);
            try (var rows = p.executeQuery()) {
              while (rows.next())
                events.add(
                    new AssignmentTimelineEvent(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        user.equals(rows.getString(4)) ? rows.getString(5) : null,
                        rows.getString(6)));
            }
          }
          return new AssignmentDetails(
              assignment,
              IncidentService.view(incidents.findById(c, assignment.incidentId())),
              List.copyOf(events.subList(0, Math.min(10, events.size()))),
              offset,
              events.size() > 10);
        });
  }
}
