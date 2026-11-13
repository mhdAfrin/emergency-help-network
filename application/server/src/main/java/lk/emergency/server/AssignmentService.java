
package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import lk.emergency.common.DomainException;
import lk.emergency.common.Dtos.AssignmentInput;
import lk.emergency.common.Dtos.AssignmentCancellationInput;
import lk.emergency.common.Dtos.AssignmentView;

public final class AssignmentService {

  private final Database database;

  private final IncidentRepository incidentRepository =
    new IncidentRepository();

  private final AssignmentRepository assignmentRepository =
    new AssignmentRepository();

  private final IncidentTimelineRepository timelineRepository =
    new IncidentTimelineRepository();

  public AssignmentService(Database database) {
    this.database = database;
  }

  // Existing public method remains available.
  public AssignmentView assignTeam(
    String operatorId,
    Role role,
    AssignmentInput input) {

    return database.transaction(
      connection -> assignTeam(
        connection,
        operatorId,
        role,
        input
      )
    );
  }

  // Uses an existing transaction managed by IdempotencyService.
  AssignmentView assignTeam(
    Connection connection,
    String operatorId,
    Role role,
    AssignmentInput input) throws Exception {

    requireOperator(operatorId, role);

    if (input == null
      || input.incidentId() == null
      || input.incidentId().isBlank()
      || input.teamId() == null
      || input.teamId().isBlank()
      || input.expectedIncidentRevision() < 0) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Incident ID, team ID and valid revision are required"
      );
    }

    IncidentRepository.Incident incident =
      incidentRepository.findById(
        connection,
        input.incidentId()
      );

    if (incident == null) {
      throw new DomainException(
        ErrorCode.NOT_FOUND,
        "Incident not found"
      );
    }

    if (!incident.state().equals(IncidentState.REPORTED.name())
      && !incident.state().equals(IncidentState.ACTIVE.name())) {

      throw new DomainException(
        ErrorCode.INVALID_TRANSITION,
        "Cannot assign a team to a closed or rejected incident"
      );
    }

    if (incident.revision() != input.expectedIncidentRevision()) {
      throw new DomainException(
        ErrorCode.VERSION_CONFLICT,
        "Incident was modified. Refresh and try again"
      );
    }

    ServiceType serviceType;

    String teamSql = """
                SELECT service_type, availability
                FROM teams
                WHERE id = ?
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(teamSql)) {

      statement.setString(1, input.teamId());

      try (ResultSet result = statement.executeQuery()) {

        if (!result.next()) {
          throw new DomainException(
            ErrorCode.NOT_FOUND,
            "Team not found"
          );
        }

        serviceType = ServiceType.valueOf(
          result.getString("service_type")
        );

        Availability availability = Availability.valueOf(
          result.getString("availability")
        );

        if (availability != Availability.AVAILABLE) {
          throw new DomainException(
            ErrorCode.TEAM_UNAVAILABLE,
            "Selected team is not available"
          );
        }
      }
    }

    String teamConflictSql = """
                SELECT COUNT(*)
                FROM assignments
                WHERE team_id = ?
                  AND state IN (
                      'OFFERED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED'
                  )
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(teamConflictSql)) {

      statement.setString(1, input.teamId());

      try (ResultSet result = statement.executeQuery()) {
        if (result.next() && result.getInt(1) > 0) {
          throw new DomainException(
            ErrorCode.ACTIVE_ASSIGNMENT_EXISTS,
            "Team already has an active assignment"
          );
        }
      }
    }

    String serviceConflictSql = """
                SELECT COUNT(*)
                FROM assignments
                WHERE incident_id = ?
                  AND service_type = ?
                  AND state IN (
                      'OFFERED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED'
                  )
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(serviceConflictSql)) {

      statement.setString(1, input.incidentId());
      statement.setString(2, serviceType.name());

      try (ResultSet result = statement.executeQuery()) {
        if (result.next() && result.getInt(1) > 0) {
          throw new DomainException(
            ErrorCode.ACTIVE_ASSIGNMENT_EXISTS,
            "An active team already provides this service"
          );
        }
      }
    }

    String assignmentId = UUID.randomUUID().toString();
    String now = Instant.now().toString();

    assignmentRepository.create(
      connection,
      assignmentId,
      input.incidentId(),
      input.teamId(),
      serviceType,
      operatorId,
      now
    );

    String updateSql = """
                UPDATE incidents
                SET state = 'ACTIVE',
                    updated_at = ?,
                    revision = revision + 1
                WHERE id = ?
                  AND revision = ?
                  AND state IN ('REPORTED', 'ACTIVE')
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(updateSql)) {

      statement.setString(1, now);
      statement.setString(2, input.incidentId());
      statement.setLong(
        3,
        input.expectedIncidentRevision()
      );

      if (statement.executeUpdate() != 1) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Incident was modified. Refresh and try again"
        );
      }
    }

    timelineRepository.create(
      connection,
      new IncidentTimelineRepository.IncidentEvent(
        UUID.randomUUID().toString(),
        input.incidentId(),
        assignmentId,
        operatorId,
        "TEAM_ASSIGNED",
        "An emergency response team has been assigned",
        null,
        now
      )
    );

    return assignmentRepository.findById(
      connection,
      assignmentId
    );
  }

  // Operators can cancel an active assignment.
  public AssignmentView cancelAssignment(
      String operatorId, Role role, AssignmentCancellationInput input) {
    return database.transaction(
        connection -> cancelAssignment(connection, operatorId, role, input));
  }

  AssignmentView cancelAssignment(
      java.sql.Connection connection,
      String operatorId, Role role, AssignmentCancellationInput input) throws Exception {

    requireOperator(operatorId, role);

    if (input == null
      || input.assignmentId() == null
      || input.assignmentId().isBlank()
      || input.reason() == null
      || input.reason().isBlank()
      || input.reason().trim().length() > 500
      || input.expectedRevision() < 0) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Assignment ID, reason (1-500 characters) "
          + "and valid revision are required"
      );
    }

    String reason = input.reason().trim();

    
    {


      AssignmentView assignment =
        assignmentRepository.findById(
          connection,
          input.assignmentId()
        );

      if (assignment == null) {
        throw new DomainException(
          ErrorCode.NOT_FOUND,
          "Assignment not found"
        );
      }

      if (assignment.revision() != input.expectedRevision()) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Assignment was modified. Refresh and try again"
        );
      }

      if (assignment.state() != AssignmentState.OFFERED
        && assignment.state() != AssignmentState.ACCEPTED
        && assignment.state() != AssignmentState.EN_ROUTE
        && assignment.state() != AssignmentState.ARRIVED) {

        throw new DomainException(
          ErrorCode.INVALID_TRANSITION,
          "This assignment cannot be cancelled"
        );
      }

      IncidentRepository.Incident incident =
        incidentRepository.findById(
          connection,
          assignment.incidentId()
        );

      if (incident == null) {
        throw new DomainException(
          ErrorCode.NOT_FOUND,
          "Related incident not found"
        );
      }

      if (!incident.state().equals(IncidentState.ACTIVE.name())
        && !incident.state().equals(IncidentState.REPORTED.name())) {

        throw new DomainException(
          ErrorCode.INVALID_TRANSITION,
          "Cannot cancel an assignment for a resolved incident"
        );
      }

      String now = Instant.now().toString();

      boolean cancelled = assignmentRepository.cancel(
        connection,
        assignment.id(),
        reason,
        now,
        input.expectedRevision()
      );

      if (!cancelled) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Assignment changed during cancellation"
        );
      }

      // Keep the incident ACTIVE so a replacement can be assigned.
      String incidentSql = """
                    UPDATE incidents
                    SET updated_at = ?,
                        revision = revision + 1
                    WHERE id = ?
                      AND revision = ?
                      AND state IN ('REPORTED', 'ACTIVE')
                    """;

      try (PreparedStatement statement =
             connection.prepareStatement(incidentSql)) {

        statement.setString(1, now);
        statement.setString(2, assignment.incidentId());
        statement.setLong(3, incident.revision());

        if (statement.executeUpdate() != 1) {
          throw new DomainException(
            ErrorCode.VERSION_CONFLICT,
            "Incident was modified. Refresh and try again"
          );
        }
      }

      timelineRepository.create(
        connection,
        new IncidentTimelineRepository.IncidentEvent(
          UUID.randomUUID().toString(),
          assignment.incidentId(),
          assignment.id(),
          operatorId,
          "ASSIGNMENT_CANCELLED",
          "An emergency team assignment was cancelled",
          reason,
          now
        )
      );

      return assignmentRepository.findById(
        connection,
        assignment.id()
      );
    
    }
  }

  private void requireOperator(String operatorId, Role role) {

    if (role != Role.OPERATOR
      || operatorId == null
      || operatorId.isBlank()) {

      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "Only operators can manage emergency team assignments"
      );
    }
  }
}
