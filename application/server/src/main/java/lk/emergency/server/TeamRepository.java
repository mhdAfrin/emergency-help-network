package lk.emergency.server;

import static lk.emergency.common.Types.*;

import java.sql.*;
import java.util.*;
import lk.emergency.common.Dtos.*;

public final class TeamRepository {
  public List<TeamView> list(
      Connection c, String responderId, java.util.function.Predicate<String> online)
      throws SQLException {
    String sql =
        "SELECT * FROM teams"
            + (responderId == null ? "" : " WHERE responder_user_id=?")
            + " ORDER BY name";
    List<TeamView> teams = new ArrayList<>();
    try (PreparedStatement p = c.prepareStatement(sql)) {
      if (responderId != null) p.setString(1, responderId);
      try (ResultSet r = p.executeQuery()) {
        while (r.next())
          teams.add(
              new TeamView(
                  r.getString("id"),
                  r.getString("name"),
                  ServiceType.valueOf(r.getString("service_type")),
                  Availability.valueOf(r.getString("availability")),
                  r.getLong("revision"),
                  online.test(r.getString("responder_user_id"))
                      ? Presence.ONLINE
                      : Presence.OFFLINE));
      }
    }
    return teams;
  }

  public void seed(Connection c, String userId, String name, ServiceType service)
      throws SQLException {
    try (PreparedStatement p =
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
}
