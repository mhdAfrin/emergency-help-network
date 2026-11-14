package lk.emergency.server.data;

import java.sql.*;
import java.time.Instant;
import lk.emergency.common.Dtos.UserView;
import lk.emergency.common.Types.Role;
import lk.emergency.server.network.*;
import lk.emergency.server.service.*;

public final class UserRepository {
  public record Account(UserView user, PasswordHasher.Hash hash) {}

  public Account find(Connection c, String username) throws SQLException {
    try (PreparedStatement p = c.prepareStatement("SELECT * FROM users WHERE username=?")) {
      p.setString(1, username);
      try (ResultSet r = p.executeQuery()) {
        if (!r.next()) return null;
        return new Account(
            new UserView(
                r.getString("id"),
                r.getString("username"),
                r.getString("display_name"),
                Role.valueOf(r.getString("role"))),
            new PasswordHasher.Hash(
                r.getBytes("password_hash"),
                r.getBytes("password_salt"),
                r.getInt("password_hash_iterations")));
      }
    }
  }

  public UserView insert(
      Connection c,
      String username,
      String display,
      String contact,
      Role role,
      PasswordHasher.Hash hash)
      throws SQLException {
    String id = ReferenceIds.next(c);
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO"
                + " users(id,username,display_name,password_hash,password_salt,password_hash_iterations,role,contact_number,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?)")) {
      p.setString(1, id);
      p.setString(2, username);
      p.setString(3, display);
      p.setBytes(4, hash.value());
      p.setBytes(5, hash.salt());
      p.setInt(6, hash.iterations());
      p.setString(7, role.name());
      p.setString(8, contact);
      p.setString(9, Instant.now().toString());
      p.executeUpdate();
    }
    return new UserView(id, username, display, role);
  }
}
