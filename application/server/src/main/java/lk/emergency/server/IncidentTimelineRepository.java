
package lk.emergency.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class IncidentTimelineRepository {

  public record IncidentEvent(
    String id,
    String incidentId,
    String assignmentId,
    String actorUserId,
    String eventType,
    String publicMessage,
    String internalNote,
    String createdAt
  ) {}

  // Save a new event in the incident timeline.
  // The caller manages the database transaction.
  public void create(Connection connection, IncidentEvent event)
    throws SQLException {

    String sql = """
                INSERT INTO incident_events (
                    id,
                    incident_id,
                    assignment_id,
                    actor_user_id,
                    event_type,
                    public_message,
                    internal_note,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, event.id());
      statement.setString(2, event.incidentId());
      statement.setString(3, event.assignmentId());
      statement.setString(4, event.actorUserId());
      statement.setString(5, event.eventType());
      statement.setString(6, event.publicMessage());
      statement.setString(7, event.internalNote());
      statement.setString(8, event.createdAt());

      statement.executeUpdate();
    }
  }

  // Retrieve the timeline of a particular incident.
  public List<IncidentEvent> findByIncident(
    Connection connection, String incidentId)
    throws SQLException {

    String sql = """
                SELECT id, incident_id, assignment_id,
                       actor_user_id, event_type, public_message,
                       internal_note, created_at
                FROM incident_events
                WHERE incident_id = ?
                ORDER BY created_at ASC, id ASC
                """;

    List<IncidentEvent> events = new ArrayList<>();

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, incidentId);

      try (ResultSet result = statement.executeQuery()) {

        while (result.next()) {
          events.add(readEvent(result));
        }
      }
    }

    return events;
  }

  // Convert one database row into an IncidentEvent.
  private IncidentEvent readEvent(ResultSet result)
    throws SQLException {

    return new IncidentEvent(
      result.getString("id"),
      result.getString("incident_id"),
      result.getString("assignment_id"),
      result.getString("actor_user_id"),
      result.getString("event_type"),
      result.getString("public_message"),
      result.getString("internal_note"),
      result.getString("created_at")
    );
  }
}
