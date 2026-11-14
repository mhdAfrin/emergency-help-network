package lk.emergency.server.network;

import static lk.emergency.common.Types.*;
import static lk.emergency.common.Types.ErrorCode.*;

import java.time.Instant;
import java.util.*;
import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;
import lk.emergency.server.data.*;
import lk.emergency.server.service.*;

/**
 * A short-lived immutable, socket-owned snapshot. Public histories remain independently paginated.
 */
public final class SnapshotService {
  public record Frozen(
      String id,
      UserView user,
      List<IncidentView> incidents,
      List<AssignmentView> assignments,
      List<TeamView> teams,
      String epoch,
      long sequence,
      String createdAt,
      long expiresAt) {}

  private final Database db;
  private final SessionRegistry registry;

  public SnapshotService(Database db, SessionRegistry registry) {
    this.db = db;
    this.registry = registry;
  }

  public SnapshotPage page(ClientSession session, SnapshotQuery query) {
    int offset = Validation.offset(query.offset());
    Frozen frozen = session.snapshot();
    if (query.snapshotId() == null) {
      if (offset != 0)
        throw new DomainException(VALIDATION_ERROR, "Start a fresh snapshot at offset 0");
      frozen = freeze(session.user());
      session.snapshot(frozen);
    } else if (frozen == null
        || !frozen.id().equals(query.snapshotId())
        || frozen.expiresAt() < System.nanoTime())
      throw new DomainException(
          INVALID_TRANSITION, "Snapshot expired or belongs to another socket; synchronize again");
    int size = frozen.incidents().size() + frozen.assignments().size() + frozen.teams().size();
    if (offset > size)
      throw new DomainException(VALIDATION_ERROR, "Snapshot offset is out of range");
    List<IncidentView> incidents = new ArrayList<>();
    List<AssignmentView> assignments = new ArrayList<>();
    List<TeamView> teams = new ArrayList<>();
    int end = Math.min(size, offset + 5);
    for (int index = offset; index < end; index++)
      if (index < frozen.incidents().size()) incidents.add(frozen.incidents().get(index));
      else if (index < frozen.incidents().size() + frozen.assignments().size())
        assignments.add(frozen.assignments().get(index - frozen.incidents().size()));
      else
        teams.add(
            frozen.teams().get(index - frozen.incidents().size() - frozen.assignments().size()));
    return new SnapshotPage(
        frozen.id(),
        offset,
        end < size ? end : null,
        frozen.user(),
        incidents,
        assignments,
        teams,
        frozen.epoch(),
        frozen.sequence(),
        frozen.createdAt());
  }

  private Frozen freeze(UserView user) {
    // Called under registry monitor after login subscription. A single DB callback cannot
    // interleave SQL work.
    return db.call(
        c -> {
          List<IncidentView> incidents = new ArrayList<>();
          List<AssignmentView> assignments = new ArrayList<>();
          String owner =
              user.role() == Role.CITIZEN
                  ? " AND citizen_id=?"
                  : user.role() == Role.RESPONDER
                      ? " AND id IN (SELECT a.incident_id FROM assignments a JOIN teams t ON"
                          + " t.id=a.team_id WHERE t.responder_user_id=? AND a.state IN"
                          + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED'))"
                      : "";
          try (var p =
              c.prepareStatement(
                  "SELECT id FROM incidents WHERE state IN ('REPORTED','ACTIVE')"
                      + owner
                      + " ORDER BY id LIMIT 5001")) {
            if (user.role() != Role.OPERATOR) p.setString(1, user.id());
            try (var rows = p.executeQuery()) {
              while (rows.next())
                incidents.add(
                    IncidentService.view(new IncidentRepository().findById(c, rows.getString(1))));
            }
          }
          String access =
              user.role() == Role.CITIZEN
                  ? " AND incident_id IN (SELECT id FROM incidents WHERE citizen_id=?)"
                  : user.role() == Role.RESPONDER
                      ? " AND team_id IN (SELECT id FROM teams WHERE responder_user_id=?)"
                      : "";
          try (var p =
              c.prepareStatement(
                  "SELECT id FROM assignments WHERE state IN"
                      + " ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED')"
                      + access
                      + " ORDER BY id LIMIT 5001")) {
            if (user.role() != Role.OPERATOR) p.setString(1, user.id());
            try (var rows = p.executeQuery()) {
              while (rows.next())
                assignments.add(new AssignmentRepository().findById(c, rows.getString(1)));
            }
          }
          List<TeamView> teams =
              user.role() == Role.CITIZEN
                  ? List.of()
                  : new TeamRepository()
                      .list(c, user.role() == Role.RESPONDER ? user.id() : null, registry::online);
          if (incidents.size() + assignments.size() + teams.size() > 5000)
            throw new DomainException(
                VALIDATION_ERROR,
                "Academic snapshot limit is 5000 active records; use paginated histories and reduce"
                    + " active load");
          return new Frozen(
              UUID.randomUUID().toString(),
              user,
              List.copyOf(incidents),
              List.copyOf(assignments),
              List.copyOf(teams),
              registry.epoch(),
              registry.sequence(),
              Instant.now().toString(),
              System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30));
        });
  }
}
