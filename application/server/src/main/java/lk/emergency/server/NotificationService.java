package lk.emergency.server;

import lk.emergency.common.*;
import lk.emergency.common.Dtos.*;

/** Called only after a successful, non-replayed mutation has committed. */
public final class NotificationService {
  private final Database db;
  private final SessionRegistry sessions;

  public NotificationService(Database db, SessionRegistry sessions) {
    this.db = db;
    this.sessions = sessions;
  }

  public void created(IncidentView incident) {
    sessions.operators(sessions.event("INCIDENT_CREATED", incident));
  }

  public void incident(IncidentView incident) {
    update(incident, null);
  }

  private void update(IncidentView incident, AssignmentView assignment) {
    var event = sessions.event("INCIDENT_UPDATED", new IncidentUpdate(incident, assignment));
    sessions.operators(event);
    sessions.user(incident.citizenId(), event);
  }

  public void team(TeamView team) {
    String user = db.call(c -> new TeamRepository().find(c, team.id()).responderUserId());
    var event = sessions.event("TEAM_UPDATED", team);
    sessions.operators(event);
    sessions.user(user, event);
  }

  public void assignment(AssignmentView assignment, boolean offered) {
    record Context(IncidentView incident, TeamRepository.Team team) {}
    Context context =
        db.call(
            c ->
                new Context(
                    IncidentService.view(
                        new IncidentRepository().findById(c, assignment.incidentId())),
                    new TeamRepository().find(c, assignment.teamId())));
    update(context.incident(), assignment);
    if (offered) {
      sessions.user(
          context.team().responderUserId(),
          sessions.event(
              "ASSIGNMENT_OFFERED", new AssignmentOffer(assignment, context.incident())));
    } else {
      // AssignmentView deliberately has no internal cancellation/decline reason.
      var event =
          sessions.event("ASSIGNMENT_UPDATED", new IncidentUpdate(context.incident(), assignment));
      sessions.operators(event);
      sessions.user(context.incident().citizenId(), event);
      sessions.user(context.team().responderUserId(), event);
    }
    team(context.team().view(sessions::online));
  }

  public void presence(String user) {
    var team = db.call(c -> new TeamRepository().forUser(c, user));
    if (team != null)
      sessions.operators(sessions.event("TEAM_UPDATED", team.view(sessions::online)));
  }
}
