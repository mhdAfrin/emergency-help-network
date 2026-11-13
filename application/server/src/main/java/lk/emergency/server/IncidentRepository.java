
package lk.emergency.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class IncidentRepository {

  public record Incident(
    String id,
    String citizenId,
    String category,
    String address,
    String description,
    Double latitude,
    Double longitude,
    String priority,
    String state,
    String publicResolutionReason,
    String createdAt,
    String updatedAt,
    String closedAt,
    long revision
  ) {}

  // Save a newly reported incident.
  public void create(Connection connection, Incident incident)
    throws SQLException {

    String sql = """
                INSERT INTO incidents (
                    id, citizen_id, category, address, description,
                    latitude, longitude, priority, state,
                    public_resolution_reason, created_at, updated_at,
                    closed_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, incident.id());
      statement.setString(2, incident.citizenId());
      statement.setString(3, incident.category());
      statement.setString(4, incident.address());
      statement.setString(5, incident.description());
      statement.setObject(6, incident.latitude());
      statement.setObject(7, incident.longitude());
      statement.setString(8, incident.priority());
      statement.setString(9, incident.state());
      statement.setString(10, incident.publicResolutionReason());
      statement.setString(11, incident.createdAt());
      statement.setString(12, incident.updatedAt());
      statement.setString(13, incident.closedAt());
      statement.setLong(14, incident.revision());

      statement.executeUpdate();
    }
  }

  // Find one incident using its ID.
  public Incident findById(
    Connection connection,
    String incidentId
  ) throws SQLException {

    String sql = "SELECT * FROM incidents WHERE id = ?";

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, incidentId);

      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? readIncident(result) : null;
      }
    }
  }

  // Return incidents reported by one citizen.
  public List<Incident> findByCitizen(
    Connection connection,
    String citizenId
  ) throws SQLException {

    String sql = """
                SELECT * FROM incidents
                WHERE citizen_id = ?
                ORDER BY created_at DESC, id DESC
                """;

    List<Incident> incidents = new ArrayList<>();

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, citizenId);

      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          incidents.add(readIncident(result));
        }
      }
    }

    return incidents;
  }

  // Return all incidents for the operator.
  public List<Incident> findAll(
    Connection connection
  ) throws SQLException {

    String sql = """
                SELECT * FROM incidents
                ORDER BY created_at DESC, id DESC
                """;

    List<Incident> incidents = new ArrayList<>();

    try (PreparedStatement statement =
           connection.prepareStatement(sql);
         ResultSet result = statement.executeQuery()) {

      while (result.next()) {
        incidents.add(readIncident(result));
      }
    }

    return incidents;
  }

  // Update priority only when the expected revision matches.
  // Closed or rejected incidents cannot be updated.
  public boolean updatePriority(
    Connection connection,
    String incidentId,
    String priority,
    String updatedAt,
    long expectedRevision
  ) throws SQLException {

    String sql = """
                UPDATE incidents
                SET priority = ?,
                    updated_at = ?,
                    revision = revision + 1
                WHERE id = ?
                  AND revision = ?
                  AND state IN ('REPORTED', 'ACTIVE')
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, priority);
      statement.setString(2, updatedAt);
      statement.setString(3, incidentId);
      statement.setLong(4, expectedRevision);

      return statement.executeUpdate() == 1;
    }
  }

  // Reject a reported incident with a public explanation.
  // An active, closed, or already rejected incident cannot be rejected.
  public boolean rejectIncident(
    Connection connection,
    String incidentId,
    String reason,
    String updatedAt,
    long expectedRevision
  ) throws SQLException {

    String sql = """
                UPDATE incidents
                SET state = 'REJECTED',
                    public_resolution_reason = ?,
                    updated_at = ?,
                    closed_at = ?,
                    revision = revision + 1
                WHERE id = ?
                  AND revision = ?
                  AND state = 'REPORTED'
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, reason);
      statement.setString(2, updatedAt);
      statement.setString(3, updatedAt);
      statement.setString(4, incidentId);
      statement.setLong(5, expectedRevision);

      return statement.executeUpdate() == 1;
    }
  }

  // Close a reported or active incident after resolution.
  // The expected revision prevents overwriting newer changes.
  // Closed and rejected incidents cannot be closed again.
  public boolean closeIncident(
    Connection connection,
    String incidentId,
    String resolutionReason,
    String updatedAt,
    long expectedRevision
  ) throws SQLException {

    String sql = """
                UPDATE incidents
                SET state = 'CLOSED',
                    public_resolution_reason = ?,
                    updated_at = ?,
                    closed_at = ?,
                    revision = revision + 1
                WHERE id = ?
                  AND revision = ?
                  AND state IN ('REPORTED', 'ACTIVE')
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, resolutionReason);
      statement.setString(2, updatedAt);
      statement.setString(3, updatedAt);
      statement.setString(4, incidentId);
      statement.setLong(5, expectedRevision);

      return statement.executeUpdate() == 1;
    }
  }

  // Convert a database row into an Incident record.
  private Incident readIncident(
    ResultSet result
  ) throws SQLException {

    Object latitude = result.getObject("latitude");
    Object longitude = result.getObject("longitude");

    return new Incident(
      result.getString("id"),
      result.getString("citizen_id"),
      result.getString("category"),
      result.getString("address"),
      result.getString("description"),
      latitude == null
        ? null
        : ((Number) latitude).doubleValue(),
      longitude == null
        ? null
        : ((Number) longitude).doubleValue(),
      result.getString("priority"),
      result.getString("state"),
      result.getString("public_resolution_reason"),
      result.getString("created_at"),
      result.getString("updated_at"),
      result.getString("closed_at"),
      result.getLong("revision")
    );
  }
}
