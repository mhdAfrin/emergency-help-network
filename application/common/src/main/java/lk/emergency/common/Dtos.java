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

  public record IncidentQuery(
      String id, Long expectedRevision, Integer assignmentOffset, Integer timelineOffset) {}

  public record IncidentFilter(
      IncidentState state, Priority priority, Integer offset, Integer limit, Boolean history) {
    public IncidentFilter(IncidentState state, Priority priority, Integer offset, Integer limit) {
      this(state, priority, offset, limit, false);
    }
  }

  public record TeamFilter(ServiceType serviceType, Availability availability, Presence presence) {}

  public record AssignmentFilter(Integer offset, Integer limit, Boolean history) {
    public AssignmentFilter(Integer offset, Integer limit) {
      this(offset, limit, false);
    }
  }

  public record PriorityUpdateInput(String id, Priority priority, Long expectedRevision) {}

  public record AssignmentInput(
      String incidentId, String teamId, Long expectedIncidentRevision, Long expectedTeamRevision) {}

  public record AssignmentCancellationInput(
      String assignmentId,
      String reason,
      Long expectedRevision,
      Long expectedIncidentRevision,
      Long expectedTeamRevision) {}

  public record IncidentRejectionInput(String incidentId, String reason, Long expectedRevision) {}

  public record TeamAvailabilityInput(Availability availability, Long expectedRevision) {}

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

  public record IncidentDetails(
      IncidentView incident,
      List<AssignmentView> assignments,
      List<PublicTimelineEvent> timeline,
      int assignmentOffset,
      int timelineOffset,
      boolean moreAssignments,
      boolean moreTimeline) {}

  public record IncidentUpdate(IncidentView incident, AssignmentView assignment) {}

  public record AssignmentOffer(
      AssignmentView assignment, IncidentView incident, String privateReason) {
    public AssignmentOffer(AssignmentView assignment, IncidentView incident) {
      this(assignment, incident, null);
    }
  }

  public record Snapshot(
      UserView user,
      List<TeamView> teams,
      List<IncidentView> incidents,
      List<AssignmentView> assignments) {}

  public enum AssignmentDecision {
    ACCEPT,
    DECLINE
  }

  public record AssignmentResponseInput(
      String assignmentId,
      AssignmentDecision decision,
      String reason,
      Long expectedRevision,
      Long expectedIncidentRevision,
      Long expectedTeamRevision) {}

  public record AssignmentStatusInput(
      String assignmentId,
      AssignmentState status,
      String note,
      Long expectedRevision,
      Long expectedIncidentRevision,
      Long expectedTeamRevision) {}

  public record IncidentClosureInput(
      String incidentId, String resolutionReason, Long expectedRevision) {}

  public record SnapshotQuery(String snapshotId, Integer offset) {}

  public record SnapshotPage(
      String snapshotId,
      int offset,
      Integer nextOffset,
      UserView user,
      List<IncidentView> incidents,
      List<AssignmentView> assignments,
      List<TeamView> teams,
      String epoch,
      long sequence,
      String createdAt) {}

  public record LiveEvent(
      String epoch, long sequence, com.fasterxml.jackson.databind.JsonNode data) {}

  public record AssignmentQuery(String id, Integer offset) {}

  public record AssignmentTimelineEvent(
      String id, String eventType, String publicMessage, String ownPrivateNote, String createdAt) {}

  public record AssignmentDetails(
      AssignmentView assignment,
      IncidentView incident,
      List<AssignmentTimelineEvent> timeline,
      int offset,
      boolean more) {}
}
