package lk.emergency.common;

import static lk.emergency.common.Types.*;

import com.fasterxml.jackson.databind.JsonNode;

public record Envelope(
    int version,
    Kind kind,
    String type,
    String requestId,
    String eventId,
    JsonNode payload,
    Boolean ok,
    ProtocolError error,
    String epoch,
    Long sequence) {
  public record ProtocolError(ErrorCode code, String message) {}

  public Envelope(
      int version,
      Kind kind,
      String type,
      String requestId,
      String eventId,
      JsonNode payload,
      Boolean ok,
      ProtocolError error) {
    this(version, kind, type, requestId, eventId, payload, ok, error, null, null);
  }

  public static Envelope request(String type, String id, Object data) {
    return new Envelope(1, Kind.REQUEST, type, id, null, Json.tree(data), null, null);
  }

  public static Envelope success(Envelope request, Object data) {
    return new Envelope(
        1, Kind.RESPONSE, request.type(), request.requestId(), null, Json.tree(data), true, null);
  }

  public static Envelope failure(Envelope request, ErrorCode code, String message) {
    return new Envelope(
        1,
        Kind.RESPONSE,
        request.type(),
        request.requestId(),
        null,
        Json.tree(java.util.Map.of()),
        false,
        new ProtocolError(code, message));
  }

  public static Envelope event(String type, Object data) {
    return new Envelope(
        1,
        Kind.EVENT,
        type,
        null,
        java.util.UUID.randomUUID().toString(),
        Json.tree(data),
        null,
        null);
  }
}
