package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DatabaseMechanismsTest {
  @TempDir Path dir;

  @Test
  void hashingCostAndSalt() {
    PasswordHasher h = new PasswordHasher();
    long start = System.nanoTime();
    PasswordHasher.Hash a = h.hash("test-password");
    long hash = System.nanoTime() - start;
    start = System.nanoTime();
    assertTrue(h.verify("test-password", a));
    long verify = System.nanoTime() - start;
    assertFalse(h.verify("wrong-password", a));
    assertFalse(Arrays.equals(a.salt(), h.hash("test-password").salt()));
    assertTrue(a.salt().length >= 16);
    assertEquals(600000, a.iterations());
    System.out.printf(
        "PBKDF2 sample (not benchmark): hash=%.1f ms verify=%.1f ms%n", hash / 1e6, verify / 1e6);
  }

  @Test
  void idempotencyRevisionAndRollback() {
    try (Database db = new Database(dir.resolve("db"))) {
      UserView user =
          new AuthService(db).register(new Registration("tester", "Test", "password!", null));
      MutationSupport mutations = new MutationSupport(db);
      String id = UUID.randomUUID().toString();
      Envelope request = Envelope.request("FUTURE_MUTATION", id, Map.of("b", 2, "a", 1));
      Database.Work<Envelope> work =
          c -> {
            try (var p = c.prepareStatement("UPDATE users SET display_name='Changed' WHERE id=?")) {
              p.setString(1, user.id());
              p.executeUpdate();
            }
            return Envelope.success(request, Map.of("saved", true));
          };
      assertFalse(mutations.execute(user.id(), request, work).replayed());
      assertTrue(
          mutations
              .execute(
                  user.id(),
                  Envelope.request("FUTURE_MUTATION", id, Map.of("a", 1, "b", 2)),
                  c -> {
                    fail("Must not execute again");
                    return null;
                  })
              .replayed());
      DomainException conflict =
          assertThrows(
              DomainException.class,
              () ->
                  mutations.execute(
                      user.id(), Envelope.request("FUTURE_MUTATION", id, Map.of("a", 3)), work));
      assertEquals(Types.ErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());
      assertThrows(
          IllegalStateException.class,
          () ->
              db.transaction(
                  c -> {
                    try (var s = c.createStatement()) {
                      s.executeUpdate("UPDATE users SET display_name='Rollback'");
                    }
                    throw new IllegalStateException("rollback");
                  }));
      assertEquals(
          "Changed", db.call(c -> new UserRepository().find(c, "tester").user().displayName()));
      assertEquals(
          Types.ErrorCode.VERSION_CONFLICT,
          assertThrows(DomainException.class, () -> MutationSupport.requireUpdated(0)).code());
      MutationSupport.requireUpdated(1);
      Envelope failed = Envelope.request("FAIL", UUID.randomUUID().toString(), Map.of());
      assertThrows(
          IllegalStateException.class,
          () ->
              mutations.execute(
                  user.id(),
                  failed,
                  c -> {
                    try (var s = c.createStatement()) {
                      s.executeUpdate("UPDATE users SET display_name='Wrong'");
                    }
                    throw new IllegalStateException();
                  }));
      assertEquals(
          Integer.valueOf(1),
          db.call(
              c -> {
                try (var s = c.createStatement();
                    var r = s.executeQuery("SELECT COUNT(*) FROM processed_requests")) {
                  r.next();
                  return r.getInt(1);
                }
              }));
    }
  }

  @Test
  void schemaConstraintsAndPartialIndexes() {
    try (Database db = new Database(dir.resolve("db"))) {
      AuthService auth = new AuthService(db);
      auth.seed();
      db.transaction(
          c -> {
            String citizen = new UserRepository().find(c, "citizen1").user().id(),
                operator = new UserRepository().find(c, "operator").user().id();
            try (var p =
                c.prepareStatement(
                    "INSERT INTO"
                        + " incidents(id,citizen_id,category,address,description,priority,state,created_at,updated_at)"
                        + " VALUES('incident',?,'FIRE','Campus','Fiction','MEDIUM','REPORTED','now','now')")) {
              p.setString(1, citizen);
              p.executeUpdate();
            }
            List<String> teams = new ArrayList<>();
            try (var s = c.createStatement();
                var r = s.executeQuery("SELECT id FROM teams WHERE service_type='FIRE_RESCUE'")) {
              while (r.next()) teams.add(r.getString(1));
            }
            try (var p =
                c.prepareStatement(
                    "INSERT INTO"
                        + " assignments(id,incident_id,team_id,service_type,assigned_by,state,created_at,updated_at)"
                        + " VALUES(?,'incident',?,'FIRE_RESCUE',?,'OFFERED','now','now')")) {
              p.setString(1, "a1");
              p.setString(2, teams.get(0));
              p.setString(3, operator);
              p.executeUpdate();
              p.setString(1, "a2");
              assertThrows(SQLException.class, p::executeUpdate);
              p.setString(2, teams.get(1));
              assertThrows(SQLException.class, p::executeUpdate);
              try (var s = c.createStatement()) {
                s.executeUpdate("UPDATE assignments SET state='DECLINED' WHERE id='a1'");
              }
              assertEquals(1, p.executeUpdate());
            }
            assertThrows(
                SQLException.class,
                () -> {
                  try (var s = c.createStatement()) {
                    s.executeUpdate("UPDATE incidents SET latitude=1,longitude=NULL");
                  }
                });
            assertThrows(
                SQLException.class,
                () -> {
                  try (var s = c.createStatement()) {
                    s.executeUpdate("UPDATE incidents SET citizen_id='missing'");
                  }
                });
            return null;
          });
    }
  }
}
