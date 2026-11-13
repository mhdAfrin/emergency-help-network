package lk.emergency.server;

import static lk.emergency.common.Types.*;
import static lk.emergency.common.Types.ErrorCode.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class AssignmentService {
  private final Database db;
  private final IncidentRepository incidents = new IncidentRepository();
  private final AssignmentRepository assignments = new AssignmentRepository();
  private final TeamRepository teams = new TeamRepository();

  public AssignmentService(Database db) {
    this.db = db;
  }

  AssignmentView assignTeam(
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
    String id = UUID.randomUUID().toString(), now = Instant.now().toString();
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

  AssignmentView cancelAssignment(
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

  TeamView changeAvailability(
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
          for (var offer : assignments.teamOffers(c, team.id(), offset, limit))
            result.add(
                new AssignmentOffer(
                    offer, IncidentService.view(incidents.findById(c, offer.incidentId()))));
          return result;
        });
  }

  private TeamRepository.Team team(Connection c, String id) throws SQLException {
    var team = teams.find(c, IncidentService.requiredId(id));
    if (team == null) throw new DomainException(NOT_FOUND, "Team not found");
    return team;
  }
}
