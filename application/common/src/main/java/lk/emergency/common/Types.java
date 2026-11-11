package lk.emergency.common;

public final class Types {
  private Types() {}

  public enum Role {
    CITIZEN,
    OPERATOR,
    RESPONDER
  }

  public enum Kind {
    REQUEST,
    RESPONSE,
    EVENT
  }

  public enum Category {
    MEDICAL,
    ROAD_ACCIDENT,
    FIRE,
    CRIME,
    OTHER
  }

  public enum ServiceType {
    POLICE,
    AMBULANCE,
    FIRE_RESCUE
  }

  public enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

  public enum IncidentState {
    REPORTED,
    ACTIVE,
    CLOSED,
    REJECTED
  }

  public enum AssignmentState {
    OFFERED,
    ACCEPTED,
    EN_ROUTE,
    ARRIVED,
    COMPLETED,
    DECLINED,
    CANCELLED
  }

  public enum Availability {
    AVAILABLE,
    BUSY,
    UNAVAILABLE
  }

  public enum Presence {
    ONLINE,
    OFFLINE
  }

  public enum RequestType {
    REGISTER_CITIZEN,
    LOGIN,
    LOGOUT,
    GET_SNAPSHOT,
    CREATE_INCIDENT,
    GET_INCIDENT,
    LIST_INCIDENTS,
    LIST_TEAMS,
    SET_PRIORITY,
    ASSIGN_TEAM,
    CANCEL_ASSIGNMENT,
    RESPOND_ASSIGNMENT,
    UPDATE_ASSIGNMENT_STATUS,
    CLOSE_INCIDENT,
    REJECT_INCIDENT,
    SET_TEAM_AVAILABILITY,
    PING,
    WHO_AM_I,
    TEST_EVENT
  }

  public enum EventType {
    INCIDENT_CREATED,
    INCIDENT_UPDATED,
    ASSIGNMENT_OFFERED,
    ASSIGNMENT_UPDATED,
    TEAM_UPDATED,
    SESSION_REPLACED,
    TEST_NOTIFICATION
  }

  public enum ErrorCode {
    VALIDATION_ERROR,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    TEAM_UNAVAILABLE,
    ACTIVE_ASSIGNMENT_EXISTS,
    INVALID_TRANSITION,
    VERSION_CONFLICT,
    IDEMPOTENCY_CONFLICT,
    UNSUPPORTED_VERSION,
    INTERNAL_ERROR
  }
}
