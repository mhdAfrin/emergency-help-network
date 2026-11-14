package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.sql.*;
import java.util.*;
import lk.emergency.common.Dtos.*;

public final class AssignmentRepository {
  public void create(
      Connection c,
      String id,
      String incidentId,
      String teamId,
      ServiceType service,
      String operatorId,
      String now)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "INSERT INTO"
                + " assignments(id,incident_id,team_id,service_type,assigned_by,state,created_at,updated_at,revision)"
                + " VALUES(?,?,?,?,?,'OFFERED',?,?,0)")) {
      p.setString(1, id);
      p.setString(2, incidentId);
      p.setString(3, teamId);
      p.setString(4, service.name());
      p.setString(5, operatorId);
      p.setString(6, now);
      p.setString(7, now);
      p.executeUpdate();
    }
  }

  public AssignmentView findById(Connection c, String id) throws SQLException {
    try (var p = c.prepareStatement("SELECT * FROM assignments WHERE id=?")) {
      p.setString(1, id);
      try (var r = p.executeQuery()) {
        return r.next() ? read(r) : null;
      }
    }
  }

  public List<AssignmentView> incidentPage(Connection c, String id, int offset, int limit)
      throws SQLException {
    return page(c, "incident_id=?", id, offset, limit);
  }

  public List<AssignmentView> teamOffers(Connection c, String teamId, int offset, int limit)
      throws SQLException {
    return page(
        c,
        "team_id=? AND state IN ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED')",
        teamId,
        offset,
        limit);
  }

  private List<AssignmentView> page(Connection c, String clause, String id, int offset, int limit)
      throws SQLException {
    List<AssignmentView> result = new ArrayList<>();
    // The clause is a server-owned constant, never user text.
    try (var p =
        c.prepareStatement(
            "SELECT * FROM assignments WHERE "
                + clause
                + " ORDER BY created_at,id LIMIT ? OFFSET ?")) {
      p.setString(1, id);
      p.setInt(2, limit);
      p.setInt(3, offset);
      try (var r = p.executeQuery()) {
        while (r.next()) result.add(read(r));
      }
    }
    return result;
  }

  public boolean teamActive(Connection c, String teamId) throws SQLException {
    return exists(
        c,
        "SELECT 1 FROM assignments WHERE team_id=? AND state IN"
            + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED')",
        teamId);
  }

  public boolean everAssigned(Connection c, String incidentId) throws SQLException {
    return exists(c, "SELECT 1 FROM assignments WHERE incident_id=?", incidentId);
  }

  public boolean serviceActive(Connection c, String id, ServiceType service) throws SQLException {
    try (var p =
        c.prepareStatement(
            "SELECT 1 FROM assignments WHERE incident_id=? AND service_type=? AND state IN"
                + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED')")) {
      p.setString(1, id);
      p.setString(2, service.name());
      try (var r = p.executeQuery()) {
        return r.next();
      }
    }
  }

  private boolean exists(Connection c, String sql, String id) throws SQLException {
    try (var p = c.prepareStatement(sql)) {
      p.setString(1, id);
      try (var r = p.executeQuery()) {
        return r.next();
      }
    }
  }

  public void cancel(Connection c, AssignmentView assignment, String reason, String now)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "UPDATE assignments SET state='CANCELLED',reason=?,updated_at=?,revision=revision+1"
                + " WHERE id=? AND revision=? AND state IN ('OFFERED','ACCEPTED')")) {
      p.setString(1, reason);
      p.setString(2, now);
      p.setString(3, assignment.id());
      p.setLong(4, assignment.revision());
      MutationSupport.requireUpdated(p.executeUpdate());
    }
  }

  private AssignmentView read(ResultSet r) throws SQLException {
    return new AssignmentView(
        r.getString("id"),
        r.getString("incident_id"),
        r.getString("team_id"),
        ServiceType.valueOf(r.getString("service_type")),
        AssignmentState.valueOf(r.getString("state")),
        r.getString("created_at"),
        r.getString("updated_at"),
        r.getLong("revision"));
  }

  public void transition(
      Connection c, AssignmentView current, AssignmentState next, String reason, String now)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "UPDATE assignments SET state=?,reason=?,updated_at=?,revision=revision+1 WHERE id=?"
                + " AND revision=? AND state=?")) {
      p.setString(1, next.name());
      p.setString(2, reason);
      p.setString(3, now);
      p.setString(4, current.id());
      p.setLong(5, current.revision());
      p.setString(6, current.state().name());
      MutationSupport.requireUpdated(p.executeUpdate());
    }
  }

  public List<AssignmentView> teamHistory(Connection c, String id, int offset, int limit)
      throws SQLException {
    return page(c, "team_id=?", id, offset, limit);
  }

  public String reason(Connection c, String id) throws SQLException {
    try (var p = c.prepareStatement("SELECT reason FROM assignments WHERE id=?")) {
      p.setString(1, id);
      try (var rows = p.executeQuery()) {
        return rows.next() ? rows.getString(1) : null;
      }
    }
  }
}
