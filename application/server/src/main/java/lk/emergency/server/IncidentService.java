package lk.emergency.server;

import static lk.emergency.common.Types.*;
import static lk.emergency.common.Types.ErrorCode.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

public final class IncidentService {
  private final Database db;
  private final IncidentRepository incidents = new IncidentRepository();
  private final AssignmentRepository assignments = new AssignmentRepository();
  private final IncidentTimelineRepository timeline = new IncidentTimelineRepository();

  public IncidentService(Database db) {
    this.db = db;
  }

  public IncidentView createIncident(String citizenId, IncidentInput input) {
    return db.transaction(c -> createIncident(c, citizenId, input));
  }

  IncidentView createIncident(Connection c, String citizenId, IncidentInput input)
      throws Exception {
    if (citizenId == null || citizenId.isBlank() || input == null || input.category() == null)
      throw new DomainException(VALIDATION_ERROR, "Citizen and category are required");
    String address = Validation.text(input.address(), 1, Validation.ADDRESS_MAX, "Address"),
        description =
            Validation.text(input.description(), 1, Validation.DESCRIPTION_MAX, "Description");
    Double lat = input.latitude(), lon = input.longitude();
    if ((lat == null) != (lon == null)
        || (lat != null
            && (!Double.isFinite(lat)
                || !Double.isFinite(lon)
                || lat < -90
                || lat > 90
                || lon < -180
                || lon > 180)))
      throw new DomainException(
          VALIDATION_ERROR, "Supply both coordinates within their valid ranges");
    String id = UUID.randomUUID().toString(), now = Instant.now().toString();
    var incident =
        new IncidentRepository.Incident(
            id,
            citizenId,
            input.category().name(),
            address,
            description,
            lat,
            lon,
            "MEDIUM",
            "REPORTED",
            null,
            now,
            now,
            null,
            0);
    incidents.create(c, incident);
    event(c, id, null, citizenId, "INCIDENT_REPORTED", "Incident reported by citizen", null, now);
    return view(incident);
  }

  public IncidentView getIncident(String userId, Role role, String id) {
    return db.call(c -> view(authorized(c, userId, role, id)));
  }

  public List<IncidentView> listIncidents(String userId, Role role) {
    return list(userId, role, new IncidentFilter(null, null, 0, 5));
  }

  public List<IncidentView> list(String userId, Role role, IncidentFilter filter) {
    requireReader(role);
    Validation.offset(filter.offset());
    Validation.limit(filter.limit());
    return db.call(
        c ->
            incidents.page(c, role == Role.CITIZEN ? userId : null, filter).stream()
                .map(IncidentService::view)
                .toList());
  }

  public IncidentDetails details(String userId, Role role, IncidentQuery query) {
    int a = Validation.offset(query.assignmentOffset()),
        t = Validation.offset(query.timelineOffset());
    return db.call(
        c -> {
          var incident = authorized(c, userId, role, query.id());
          List<AssignmentView> servicePage = assignments.incidentPage(c, incident.id(), a, 11);
          List<PublicTimelineEvent> publicPage = new ArrayList<>();
          try (var p =
              c.prepareStatement(
                  "SELECT id,incident_id,assignment_id,event_type,public_message,created_at FROM"
                      + " incident_events WHERE incident_id=? ORDER BY created_at,id LIMIT 11"
                      + " OFFSET ?")) {
            p.setString(1, incident.id());
            p.setInt(2, t);
            try (var rows = p.executeQuery()) {
              while (rows.next())
                publicPage.add(
                    new PublicTimelineEvent(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getString(5),
                        rows.getString(6)));
            }
          }
          return new IncidentDetails(
              view(incident),
              List.copyOf(servicePage.subList(0, Math.min(10, servicePage.size()))),
              List.copyOf(publicPage.subList(0, Math.min(10, publicPage.size()))),
              a,
              t,
              servicePage.size() > 10,
              publicPage.size() > 10);
        });
  }

  public IncidentView updatePriority(String operatorId, Role role, PriorityUpdateInput input) {
    return db.transaction(c -> updatePriority(c, operatorId, role, input));
  }

  IncidentView updatePriority(Connection c, String operatorId, Role role, PriorityUpdateInput input)
      throws Exception {
    requireOperator(role);
    long expected = Validation.revision(input.expectedRevision());
    if (input.priority() == null)
      throw new DomainException(VALIDATION_ERROR, "Priority is required");
    var current = mutable(c, input.id(), expected);
    String now = Instant.now().toString();
    MutationSupport.requireUpdated(
        incidents.updatePriority(c, current.id(), input.priority().name(), now, expected) ? 1 : 0);
    event(
        c,
        current.id(),
        null,
        operatorId,
        "PRIORITY_UPDATED",
        "Priority changed to " + input.priority(),
        null,
        now);
    return view(incidents.findById(c, current.id()));
  }

  public IncidentView rejectIncident(String operatorId, Role role, IncidentRejectionInput input) {
    return db.transaction(c -> rejectIncident(c, operatorId, role, input));
  }

  IncidentView rejectIncident(
      Connection c, String operatorId, Role role, IncidentRejectionInput input) throws Exception {
    requireOperator(role);
    long expected = Validation.revision(input.expectedRevision());
    String reason = Validation.text(input.reason(), 1, 500, "Public rejection reason");
    var current = mutable(c, input.incidentId(), expected);
    if (!current.state().equals("REPORTED") || assignments.everAssigned(c, current.id()))
      throw new DomainException(
          INVALID_TRANSITION, "Only reports that have never had an assignment can be rejected");
    String now = Instant.now().toString();
    MutationSupport.requireUpdated(
        incidents.rejectIncident(c, current.id(), reason, now, expected) ? 1 : 0);
    event(
        c,
        current.id(),
        null,
        operatorId,
        "INCIDENT_REJECTED",
        "Incident rejected: " + reason,
        null,
        now);
    return view(incidents.findById(c, current.id()));
  }

  IncidentRepository.Incident mutable(Connection c, String id, long expected) throws SQLException {
    var incident = incidents.findById(c, requiredId(id));
    if (incident == null) throw new DomainException(NOT_FOUND, "Incident not found");
    if (incident.revision() != expected)
      throw new DomainException(
          VERSION_CONFLICT, "Incident changed; refresh and review before retrying");
    if (!incident.state().equals("REPORTED") && !incident.state().equals("ACTIVE"))
      throw new DomainException(INVALID_TRANSITION, "Incident is terminal");
    return incident;
  }

  private IncidentRepository.Incident authorized(Connection c, String userId, Role role, String id)
      throws SQLException {
    requireReader(role);
    var incident = incidents.findById(c, requiredId(id));
    if (incident == null || (role == Role.CITIZEN && !incident.citizenId().equals(userId)))
      throw new DomainException(NOT_FOUND, "Incident not found");
    return incident;
  }

  static String requiredId(String id) {
    return Validation.text(id, 1, 80, "ID");
  }

  static void requireOperator(Role role) {
    if (role != Role.OPERATOR) throw new DomainException(FORBIDDEN, "Operator access required");
  }

  private void requireReader(Role role) {
    if (role != Role.OPERATOR && role != Role.CITIZEN)
      throw new DomainException(FORBIDDEN, "Action not permitted for this role");
  }

  void event(
      Connection c,
      String incidentId,
      String assignmentId,
      String actor,
      String type,
      String message,
      String note,
      String now)
      throws SQLException {
    timeline.create(
        c,
        new IncidentTimelineRepository.IncidentEvent(
            UUID.randomUUID().toString(),
            incidentId,
            assignmentId,
            actor,
            type,
            message,
            note,
            now));
  }

  static IncidentView view(IncidentRepository.Incident x) {
    return new IncidentView(
        x.id(),
        x.citizenId(),
        Category.valueOf(x.category()),
        x.address(),
        x.description(),
        x.latitude(),
        x.longitude(),
        Priority.valueOf(x.priority()),
        IncidentState.valueOf(x.state()),
        x.publicResolutionReason(),
        x.createdAt(),
        x.updatedAt(),
        x.closedAt(),
        x.revision());
  }

  IncidentView closeIncident(Connection c, String actor, IncidentClosureInput input)
      throws Exception {
    long revision = Validation.revision(input.expectedRevision());
    String reason = Validation.text(input.resolutionReason(), 1, 500, "Public resolution");
    var incident = mutable(c, input.incidentId(), revision);
    if (!incident.state().equals("ACTIVE"))
      throw new DomainException(INVALID_TRANSITION, "Only ACTIVE incidents can be closed");
    try (var p =
        c.prepareStatement(
            "SELECT SUM(CASE WHEN state='COMPLETED' THEN 1 ELSE 0 END),SUM(CASE WHEN state IN"
                + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED') THEN 1 ELSE 0 END) FROM assignments"
                + " WHERE incident_id=?")) {
      p.setString(1, incident.id());
      try (var rows = p.executeQuery()) {
        rows.next();
        if (rows.getInt(1) < 1 || rows.getInt(2) > 0)
          throw new DomainException(
              INVALID_TRANSITION,
              "Closure requires completed assistance and every assignment terminal");
      }
    }
    String now = Instant.now().toString();
    try (var p =
        c.prepareStatement(
            "UPDATE incidents SET"
                + " state='CLOSED',public_resolution_reason=?,closed_at=?,updated_at=?,revision=revision+1"
                + " WHERE id=? AND revision=? AND state='ACTIVE'")) {
      p.setString(1, reason);
      p.setString(2, now);
      p.setString(3, now);
      p.setString(4, incident.id());
      p.setLong(5, revision);
      MutationSupport.requireUpdated(p.executeUpdate());
    }
    event(
        c, incident.id(), null, actor, "INCIDENT_CLOSED", "Incident closed: " + reason, null, now);
    return view(incidents.findById(c, incident.id()));
  }

  public com.fasterxml.jackson.databind.JsonNode detailPayload(
      String user, Role role, IncidentQuery query) {
    var payload =
        (com.fasterxml.jackson.databind.node.ObjectNode) Json.tree(details(user, role, query));
    if (role == Role.OPERATOR) {
      var notes =
          db.call(
              c -> {
                List<Map<String, Object>> result = new ArrayList<>();
                try (var p =
                    c.prepareStatement(
                        "SELECT id,event_type,internal_note,created_at FROM incident_events WHERE"
                            + " incident_id=? ORDER BY created_at,id LIMIT 10 OFFSET ?")) {
                  p.setString(1, query.id());
                  p.setInt(2, Validation.offset(query.timelineOffset()));
                  try (var rows = p.executeQuery()) {
                    while (rows.next()) {
                      String note = rows.getString(3);
                      if (note != null)
                        result.add(
                            Map.of(
                                "id",
                                rows.getString(1),
                                "eventType",
                                rows.getString(2),
                                "internalNote",
                                note,
                                "createdAt",
                                rows.getString(4)));
                    }
                  }
                }
                return result;
              });
      payload.set("operationalNotes", Json.tree(notes));
    }
    return payload;
  }
}
