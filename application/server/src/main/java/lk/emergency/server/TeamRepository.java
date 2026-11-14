package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.sql.*;
import java.util.*;
import java.util.function.Predicate;
import lk.emergency.common.Dtos.*;

public final class TeamRepository {
  public record Team(
      String id,
      String responderUserId,
      String name,
      ServiceType serviceType,
      Availability availability,
      long revision) {
    public TeamView view(Predicate<String> online) {
      return new TeamView(
          id,
          name,
          serviceType,
          availability,
          revision,
          online.test(responderUserId) ? Presence.ONLINE : Presence.OFFLINE);
    }
  }

  public Team find(Connection c, String id) throws SQLException {
    try (var p = c.prepareStatement("SELECT * FROM teams WHERE id=?")) {
      p.setString(1, id);
      try (var rows = p.executeQuery()) {
        return rows.next() ? read(rows) : null;
      }
    }
  }

  public Team forUser(Connection c, String id) throws SQLException {
    try (var p = c.prepareStatement("SELECT * FROM teams WHERE responder_user_id=?")) {
      p.setString(1, id);
      try (var rows = p.executeQuery()) {
        return rows.next() ? read(rows) : null;
      }
    }
  }

  public List<TeamView> list(Connection c, String responderId, Predicate<String> online)
      throws SQLException {
    List<TeamView> result = new ArrayList<>();
    try (var p =
        c.prepareStatement(
            "SELECT * FROM teams"
                + (responderId == null ? "" : " WHERE responder_user_id=?")
                + " ORDER BY name")) {
      if (responderId != null) p.setString(1, responderId);
      try (var rows = p.executeQuery()) {
        while (rows.next()) result.add(read(rows).view(online));
      }
    }
    return result;
  }

  public void availability(Connection c, Team team, Availability next) throws SQLException {
    try (var p =
        c.prepareStatement(
            "UPDATE teams SET availability=?,revision=revision+1 WHERE id=? AND revision=? AND"
                + " availability=?")) {
      p.setString(1, next.name());
      p.setString(2, team.id());
      p.setLong(3, team.revision());
      p.setString(4, team.availability().name());
      MutationSupport.requireUpdated(p.executeUpdate());
    }
  }

  public void seed(Connection c, String userId, String name, ServiceType service)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "INSERT INTO teams(id,responder_user_id,name,service_type,availability,revision)"
                + " VALUES(?,?,?,?,'AVAILABLE',0) ON CONFLICT(responder_user_id) DO NOTHING")) {
      p.setString(1, UUID.randomUUID().toString());
      p.setString(2, userId);
      p.setString(3, name);
      p.setString(4, service.name());
      p.executeUpdate();
    }
  }

  private Team read(ResultSet r) throws SQLException {
    return new Team(
        r.getString("id"),
        r.getString("responder_user_id"),
        r.getString("name"),
        ServiceType.valueOf(r.getString("service_type")),
        Availability.valueOf(r.getString("availability")),
        r.getLong("revision"));
  }

  public void reconcile(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.executeUpdate(
          "UPDATE teams SET availability='BUSY',revision=revision+1 WHERE availability<>'BUSY' AND"
              + " EXISTS(SELECT 1 FROM assignments WHERE team_id=teams.id AND state IN"
              + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED'))");
      s.executeUpdate(
          "UPDATE teams SET availability='AVAILABLE',revision=revision+1 WHERE availability='BUSY'"
              + " AND NOT EXISTS(SELECT 1 FROM assignments WHERE team_id=teams.id AND state IN"
              + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED'))");
    }
  }
}
