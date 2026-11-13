
package lk.emergency.server;

import static org.junit.jupiter.api.Assertions.*;
import static lk.emergency.common.Types.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import lk.emergency.common.DomainException;
import lk.emergency.common.Dtos.IncidentInput;
import lk.emergency.common.Dtos.IncidentView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncidentValidationTest {

  @TempDir
  Path tempDir;

  private record InvalidCase(
    String name,
    IncidentInput input) {}

  @Test
  void rejectsInvalidCoordinatesWithoutSavingAnything() throws Exception {
    try (Database database = new Database(tempDir.resolve("invalid.db"))) {
      AuthService auth = new AuthService(database);
      auth.seed();

      String citizenId = citizenId(database);
      IncidentService service = new IncidentService(database);

      List<InvalidCase> cases = List.of(
        new InvalidCase("latitude only",
          input(6.9271, null)),
        new InvalidCase("longitude only",
          input(null, 79.8612)),
        new InvalidCase("latitude above maximum",
          input(90.0001, 79.8612)),
        new InvalidCase("latitude below minimum",
          input(-90.0001, 79.8612)),
        new InvalidCase("longitude above maximum",
          input(6.9271, 180.0001)),
        new InvalidCase("longitude below minimum",
          input(6.9271, -180.0001)),
        new InvalidCase("latitude NaN",
          input(Double.NaN, 79.8612)),
        new InvalidCase("longitude NaN",
          input(6.9271, Double.NaN)),
        new InvalidCase("latitude infinity",
          input(Double.POSITIVE_INFINITY, 79.8612)),
        new InvalidCase("longitude infinity",
          input(6.9271, Double.NEGATIVE_INFINITY))
      );

      for (InvalidCase testCase : cases) {
        DomainException error = assertThrows(
          DomainException.class,
          () -> service.createIncident(citizenId, testCase.input()),
          testCase.name()
        );

        assertEquals(
          ErrorCode.VALIDATION_ERROR,
          error.code(),
          testCase.name()
        );
      }

      assertEquals(0, count(database, "incidents"));
      assertEquals(0, count(database, "incident_events"));
    }
  }

  @Test
  void acceptsValidCoordinateBoundaries() throws Exception {
    try (Database database = new Database(tempDir.resolve("valid.db"))) {
      new AuthService(database).seed();

      String citizenId = citizenId(database);
      IncidentService service = new IncidentService(database);

      IncidentView minimum = service.createIncident(
        citizenId,
        input(-90.0, -180.0)
      );

      IncidentView maximum = service.createIncident(
        citizenId,
        input(90.0, 180.0)
      );

      IncidentView withoutCoordinates = service.createIncident(
        citizenId,
        input(null, null)
      );

      assertEquals(-90.0, minimum.latitude());
      assertEquals(-180.0, minimum.longitude());
      assertEquals(90.0, maximum.latitude());
      assertEquals(180.0, maximum.longitude());

      assertNull(withoutCoordinates.latitude());
      assertNull(withoutCoordinates.longitude());

      assertEquals(3, count(database, "incidents"));
      assertEquals(3, count(database, "incident_events"));
    }
  }

  @Test
  void rejectsInvalidRequiredFields() throws Exception {
    try (Database database = new Database(tempDir.resolve("fields.db"))) {
      new AuthService(database).seed();

      String citizenId = citizenId(database);
      IncidentService service = new IncidentService(database);

      List<IncidentInput> invalid = List.of(
        new IncidentInput(null, "Colombo", "Test", null, null),
        new IncidentInput(Category.FIRE, "", "Test", null, null),
        new IncidentInput(Category.FIRE, "A".repeat(201), "Test", null, null),
        new IncidentInput(Category.FIRE, "Colombo", "", null, null),
        new IncidentInput(Category.FIRE, "Colombo",
          "A".repeat(1001), null, null)
      );

      for (IncidentInput input : invalid) {
        DomainException error = assertThrows(
          DomainException.class,
          () -> service.createIncident(citizenId, input)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
      }

      assertEquals(0, count(database, "incidents"));
      assertEquals(0, count(database, "incident_events"));
    }
  }

  @Test
  void rejectsOutdatedPriorityRevisionWithoutChangingDatabase() throws Exception {
    try (Database database = new Database(tempDir.resolve("priority.db"))) {
      new AuthService(database).seed();

      String citizenId = citizenId(database);
      String operatorId = database.call(connection -> {
        try (var statement = connection.prepareStatement(
            "SELECT id FROM users WHERE username = ?")) {
          statement.setString(1, "operator");
          try (var result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
          }
        }
      });

      IncidentService service = new IncidentService(database);

      IncidentView created = service.createIncident(
          citizenId,
          input(null, null)
      );

      assertEquals(0L, created.revision());

      IncidentView updated = service.updatePriority(
          operatorId,
          Role.OPERATOR,
          new lk.emergency.common.Dtos.PriorityUpdateInput(
              created.id(), Priority.HIGH, 0L)
      );

      assertEquals(Priority.HIGH, updated.priority());
      assertEquals(1L, updated.revision());

      DomainException error = assertThrows(
          DomainException.class,
          () -> service.updatePriority(
              operatorId,
              Role.OPERATOR,
              new lk.emergency.common.Dtos.PriorityUpdateInput(
                  created.id(), Priority.LOW, 0L)
          )
      );

      assertEquals(ErrorCode.VERSION_CONFLICT, error.code());

      IncidentView saved = service.getIncident(
          citizenId, Role.CITIZEN, created.id()
      );

      assertEquals(Priority.HIGH, saved.priority());
      assertEquals(1L, saved.revision());

      assertEquals(1, count(database, "incidents"));
      assertEquals(2, count(database, "incident_events"));
    }
  }
  private IncidentInput input(Double latitude, Double longitude) {
    return new IncidentInput(
      Category.FIRE,
      "Colombo",
      "Emergency test " + UUID.randomUUID(),
      latitude,
      longitude
    );
  }

  private String citizenId(Database database) {
    return database.call(connection -> {
      try (var statement = connection.prepareStatement(
        "SELECT id FROM users WHERE username = ?")) {
        statement.setString(1, "citizen1");

        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
          return result.getString(1);
        }
      }
    });
  }

  private long count(Database database, String table) {
    return database.call(connection -> {
      try (var statement = connection.createStatement();
           var result = statement.executeQuery(
             "SELECT COUNT(*) FROM " + table)) {
        result.next();
        return result.getLong(1);
      }
    });
  }
}
