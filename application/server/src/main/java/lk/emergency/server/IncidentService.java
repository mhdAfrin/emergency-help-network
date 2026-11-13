
package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lk.emergency.common.DomainException;
import lk.emergency.common.Dtos.IncidentClosureInput;
import lk.emergency.common.Dtos.IncidentInput;
import lk.emergency.common.Dtos.IncidentRejectionInput;
import lk.emergency.common.Dtos.IncidentView;
import lk.emergency.common.Dtos.PriorityUpdateInput;
import lk.emergency.common.Validation;

public final class IncidentService {

  private final Database database;
  private final IncidentRepository incidentRepository =
    new IncidentRepository();
  private final IncidentTimelineRepository timelineRepository =
    new IncidentTimelineRepository();

  public IncidentService(Database database) {
    this.database = database;
  }

  // Normal incident creation.
  public IncidentView createIncident(
    String citizenId,
    IncidentInput input) {

    return database.transaction(
      connection -> createIncident(connection, citizenId, input)
    );
  }

  // Uses the transaction provided by IdempotencyService.
  IncidentView createIncident(
    Connection connection,
    String citizenId,
    IncidentInput input) throws Exception {

    if (citizenId == null || citizenId.isBlank()) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Citizen ID is required"
      );
    }

    if (input == null || input.category() == null) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Incident category is required"
      );
    }

    String address = Validation.text(
      input.address(),
      1,
      Validation.ADDRESS_MAX,
      "Address"
    );

    String description = Validation.text(
      input.description(),
      1,
      Validation.DESCRIPTION_MAX,
      "Description"
    );

    Double latitude = input.latitude();
    Double longitude = input.longitude();

    if ((latitude == null) != (longitude == null)) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Latitude and longitude must be provided together"
      );
    }

    if (latitude != null) {
      if (!Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90
        || latitude > 90
        || longitude < -180
        || longitude > 180) {

        throw new DomainException(
          ErrorCode.VALIDATION_ERROR,
          "Invalid geographical coordinates"
        );
      }
    }

    String incidentId = UUID.randomUUID().toString();
    String eventId = UUID.randomUUID().toString();
    String now = Instant.now().toString();

    IncidentRepository.Incident incident =
      new IncidentRepository.Incident(
        incidentId,
        citizenId,
        input.category().name(),
        address,
        description,
        latitude,
        longitude,
        Priority.MEDIUM.name(),
        IncidentState.REPORTED.name(),
        null,
        now,
        now,
        null,
        0L
      );

    IncidentTimelineRepository.IncidentEvent event =
      new IncidentTimelineRepository.IncidentEvent(
        eventId,
        incidentId,
        null,
        citizenId,
        "INCIDENT_REPORTED",
        "Incident reported by citizen",
        null,
        now
      );

    incidentRepository.create(connection, incident);
    timelineRepository.create(connection, event);

    return toView(incident);
  }

  // Citizens can view their own incidents.
  // Operators can view any incident.
  public IncidentView getIncident(
    String userId,
    Role role,
    String incidentId) {

    if (incidentId == null || incidentId.isBlank()) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Incident ID is required"
      );
    }

    IncidentRepository.Incident incident = database.call(
      connection -> incidentRepository.findById(
        connection,
        incidentId
      )
    );

    if (incident == null) {
      throw new DomainException(
        ErrorCode.NOT_FOUND,
        "Incident not found"
      );
    }

    if (role == Role.CITIZEN
      && !incident.citizenId().equals(userId)) {

      throw new DomainException(
        ErrorCode.NOT_FOUND,
        "Incident not found"
      );
    }

    if (role != Role.CITIZEN && role != Role.OPERATOR) {
      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "You cannot view this incident"
      );
    }

    return toView(incident);
  }

  // Citizens see their reports; operators see all reports.
  public List<IncidentView> listIncidents(
    String userId,
    Role role) {

    List<IncidentRepository.Incident> incidents;

    if (role == Role.CITIZEN) {
      incidents = database.call(
        connection -> incidentRepository.findByCitizen(
          connection,
          userId
        )
      );
    } else if (role == Role.OPERATOR) {
      incidents = database.call(
        incidentRepository::findAll
      );
    } else {
      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "You cannot list incidents"
      );
    }

    return incidents.stream()
      .map(this::toView)
      .toList();
  }

  // Public method remains available for existing callers.
  public IncidentView updatePriority(
    String operatorId,
    Role role,
    PriorityUpdateInput input) {

    return database.transaction(
      connection -> updatePriority(
        connection,
        operatorId,
        role,
        input
      )
    );
  }

  // Runs inside an existing transaction for idempotent requests.
  IncidentView updatePriority(
    Connection connection,
    String operatorId,
    Role role,
    PriorityUpdateInput input) throws Exception {

    if (role != Role.OPERATOR) {
      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "Only operators can change incident priority"
      );
    }

    if (operatorId == null || operatorId.isBlank()
      || input == null
      || input.id() == null
      || input.id().isBlank()
      || input.priority() == null
      || input.expectedRevision() < 0) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Valid incident ID, priority and revision are required"
      );
    }

    IncidentRepository.Incident current =
      incidentRepository.findById(
        connection,
        input.id()
      );

    if (current == null) {
      throw new DomainException(
        ErrorCode.NOT_FOUND,
        "Incident not found"
      );
    }

    if (!current.state().equals(IncidentState.REPORTED.name())
      && !current.state().equals(IncidentState.ACTIVE.name())) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Cannot change priority of a closed or rejected incident"
      );
    }

    if (current.revision() != input.expectedRevision()) {
      throw new DomainException(
        ErrorCode.VERSION_CONFLICT,
        "Incident was modified. Refresh and try again"
      );
    }

    String now = Instant.now().toString();

    boolean updated = incidentRepository.updatePriority(
      connection,
      input.id(),
      input.priority().name(),
      now,
      input.expectedRevision()
    );

    if (!updated) {
      throw new DomainException(
        ErrorCode.VERSION_CONFLICT,
        "Priority update failed. Refresh and try again"
      );
    }

    IncidentTimelineRepository.IncidentEvent event =
      new IncidentTimelineRepository.IncidentEvent(
        UUID.randomUUID().toString(),
        input.id(),
        null,
        operatorId,
        "PRIORITY_CHANGED",
        "Incident priority changed from "
          + current.priority()
          + " to "
          + input.priority().name(),
        null,
        now
      );

    timelineRepository.create(connection, event);

    IncidentRepository.Incident updatedIncident =
      incidentRepository.findById(
        connection,
        input.id()
      );

    return toView(updatedIncident);
  }

  // Reject an invalid or duplicate incident.
  public IncidentView rejectIncident(
      String operatorId, Role role, IncidentRejectionInput input) {
    return database.transaction(
        connection -> rejectIncident(connection, operatorId, role, input));
  }

  IncidentView rejectIncident(
      java.sql.Connection connection,
      String operatorId, Role role, IncidentRejectionInput input) throws Exception {

    if (role != Role.OPERATOR) {
      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "Only operators can reject incidents"
      );
    }

    if (operatorId == null || operatorId.isBlank()
      || input == null
      || input.incidentId() == null
      || input.incidentId().isBlank()
      || input.expectedRevision() < 0) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Valid incident ID and revision are required"
      );
    }

    String reason = input.reason() == null
      ? ""
      : input.reason().strip();

    if (reason.isEmpty() || reason.length() > 500) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Rejection reason must contain 1-500 characters"
      );
    }

    
    {


      IncidentRepository.Incident current =
        incidentRepository.findById(
          connection,
          input.incidentId()
        );

      if (current == null) {
        throw new DomainException(
          ErrorCode.NOT_FOUND,
          "Incident not found"
        );
      }

      if (current.revision() != input.expectedRevision()) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Incident was modified. Refresh and try again"
        );
      }

      if (!current.state().equals(IncidentState.REPORTED.name())) {
        throw new DomainException(
          ErrorCode.INVALID_TRANSITION,
          "Only reported incidents can be rejected"
        );
      }

      String now = Instant.now().toString();

      boolean rejected = incidentRepository.rejectIncident(
        connection,
        input.incidentId(),
        reason,
        now,
        input.expectedRevision()
      );

      if (!rejected) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Incident rejection failed. Refresh and try again"
        );
      }

      IncidentTimelineRepository.IncidentEvent event =
        new IncidentTimelineRepository.IncidentEvent(
          UUID.randomUUID().toString(),
          input.incidentId(),
          null,
          operatorId,
          "INCIDENT_REJECTED",
          "Incident rejected: " + reason,
          null,
          now
        );

      timelineRepository.create(connection, event);

      IncidentRepository.Incident updatedIncident =
        incidentRepository.findById(
          connection,
          input.incidentId()
        );

      return toView(updatedIncident);
    
    }
  }

  // Close a resolved incident.
  public IncidentView closeIncident(
      String operatorId, Role role, IncidentClosureInput input) {
    return database.transaction(
        connection -> closeIncident(connection, operatorId, role, input));
  }

  IncidentView closeIncident(
      java.sql.Connection connection,
      String operatorId, Role role, IncidentClosureInput input) throws Exception {

    if (role != Role.OPERATOR) {
      throw new DomainException(
        ErrorCode.FORBIDDEN,
        "Only operators can close incidents"
      );
    }

    if (operatorId == null || operatorId.isBlank()
      || input == null
      || input.incidentId() == null
      || input.incidentId().isBlank()
      || input.expectedRevision() < 0) {

      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Valid incident ID and revision are required"
      );
    }

    String reason = input.resolutionReason() == null
      ? ""
      : input.resolutionReason().strip();

    if (reason.isEmpty() || reason.length() > 500) {
      throw new DomainException(
        ErrorCode.VALIDATION_ERROR,
        "Resolution reason must contain 1-500 characters"
      );
    }

    
    {


      IncidentRepository.Incident current =
        incidentRepository.findById(
          connection,
          input.incidentId()
        );

      if (current == null) {
        throw new DomainException(
          ErrorCode.NOT_FOUND,
          "Incident not found"
        );
      }

      if (current.revision() != input.expectedRevision()) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Incident was modified. Refresh and try again"
        );
      }

      if (!current.state().equals(IncidentState.REPORTED.name())
        && !current.state().equals(IncidentState.ACTIVE.name())) {

        throw new DomainException(
          ErrorCode.INVALID_TRANSITION,
          "Only reported or active incidents can be closed"
        );
      }

      String now = Instant.now().toString();

      boolean closed = incidentRepository.closeIncident(
        connection,
        input.incidentId(),
        reason,
        now,
        input.expectedRevision()
      );

      if (!closed) {
        throw new DomainException(
          ErrorCode.VERSION_CONFLICT,
          "Incident closure failed. Refresh and try again"
        );
      }

      IncidentTimelineRepository.IncidentEvent event =
        new IncidentTimelineRepository.IncidentEvent(
          UUID.randomUUID().toString(),
          input.incidentId(),
          null,
          operatorId,
          "INCIDENT_CLOSED",
          "Incident closed: " + reason,
          null,
          now
        );

      timelineRepository.create(connection, event);

      IncidentRepository.Incident updatedIncident =
        incidentRepository.findById(
          connection,
          input.incidentId()
        );

      return toView(updatedIncident);
    
    }
  }

  // Convert the database record into a response DTO.
  private IncidentView toView(
    IncidentRepository.Incident incident) {

    return new IncidentView(
      incident.id(),
      incident.citizenId(),
      Category.valueOf(incident.category()),
      incident.address(),
      incident.description(),
      incident.latitude(),
      incident.longitude(),
      Priority.valueOf(incident.priority()),
      IncidentState.valueOf(incident.state()),
      incident.publicResolutionReason(),
      incident.createdAt(),
      incident.updatedAt(),
      incident.closedAt(),
      incident.revision()
    );
  }
}

