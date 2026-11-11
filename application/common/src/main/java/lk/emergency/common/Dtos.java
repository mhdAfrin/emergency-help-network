package lk.emergency.common;

import static lk.emergency.common.Types.*;

import java.util.List;

public final class Dtos {
  private Dtos() {}

  public record Registration(
      String username, String displayName, String password, String contactNumber) {}

  public record Login(String username, String password) {}

  public record UserView(String id, String username, String displayName, Role role) {}

  public record TeamView(
      String id,
      String name,
      ServiceType serviceType,
      Availability availability,
      long revision,
      Presence presence) {}

  public record IncidentInput(
      Category category, String address, String description, Double latitude, Double longitude) {}

  public record RevisionInput(String id, long expectedRevision) {}

  public record IncidentView(
      String id,
      String citizenId,
      Category category,
      String address,
      String description,
      Double latitude,
      Double longitude,
      Priority priority,
      IncidentState state,
      String publicResolutionReason,
      String createdAt,
      String updatedAt,
      String closedAt,
      long revision) {}

  public record AssignmentView(
      String id,
      String incidentId,
      String teamId,
      ServiceType serviceType,
      AssignmentState state,
      String createdAt,
      String updatedAt,
      long revision) {}

  public record PublicTimelineEvent(
      String id,
      String incidentId,
      String assignmentId,
      String eventType,
      String publicMessage,
      String createdAt) {}

  public record Snapshot(
      UserView user,
      List<TeamView> teams,
      List<IncidentView> incidents,
      List<AssignmentView> assignments) {}
}
