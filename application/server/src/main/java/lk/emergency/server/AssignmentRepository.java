
package lk.emergency.server;

import lk.emergency.common.Dtos.AssignmentView;
import lk.emergency.common.Types.AssignmentState;
import lk.emergency.common.Types.ServiceType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class AssignmentRepository {

  public void create(
    Connection connection,
    String id,
    String incidentId,
    String teamId,
    ServiceType serviceType,
    String assignedBy,
    String createdAt
  ) throws SQLException {

    String sql = """
                INSERT INTO assignments (
                    id, incident_id, team_id, service_type,
                    assigned_by, state, reason,
                    created_at, updated_at, revision
                )
                VALUES (?, ?, ?, ?, ?, 'OFFERED', NULL, ?, ?, 0)
                """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.setString(2, incidentId);
      statement.setString(3, teamId);
      statement.setString(4, serviceType.name());
      statement.setString(5, assignedBy);
      statement.setString(6, createdAt);
      statement.setString(7, createdAt);
      statement.executeUpdate();
    }
  }

  public AssignmentView findById(
    Connection connection,
    String assignmentId
  ) throws SQLException {

    String sql = "SELECT * FROM assignments WHERE id = ?";

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, assignmentId);

      try (ResultSet result = statement.executeQuery()) {
        if (result.next()) {
          return toView(result);
        }
      }
    }

    return null;
  }

  public List<AssignmentView> findByIncident(
    Connection connection,
    String incidentId
  ) throws SQLException {

    String sql = """
                SELECT * FROM assignments
                WHERE incident_id = ?
                ORDER BY created_at
                """;

    List<AssignmentView> assignments = new ArrayList<>();

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, incidentId);

      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          assignments.add(toView(result));
        }
      }
    }

    return assignments;
  }

  public boolean cancel(
    Connection connection,
    String assignmentId,
    String reason,
    String updatedAt,
    long expectedRevision
  ) throws SQLException {

    String sql = """
                UPDATE assignments
                SET state = 'CANCELLED',
                    reason = ?,
                    updated_at = ?,
                    revision = revision + 1
                WHERE id = ?
                  AND revision = ?
                  AND state IN ('OFFERED', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED')
                """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, reason);
      statement.setString(2, updatedAt);
      statement.setString(3, assignmentId);
      statement.setLong(4, expectedRevision);

      return statement.executeUpdate() == 1;
    }
  }

  private AssignmentView toView(ResultSet result) throws SQLException {
    return new AssignmentView(
      result.getString("id"),
      result.getString("incident_id"),
      result.getString("team_id"),
      ServiceType.valueOf(result.getString("service_type")),
      AssignmentState.valueOf(result.getString("state")),
      result.getString("created_at"),
      result.getString("updated_at"),
      result.getLong("revision")
    );
  }
}
