package lk.emergency.server;

import static lk.emergency.common.Types.ErrorCode.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import lk.emergency.common.*;

/** Call business SQL on the supplied connection; never recursively call Database inside work. */
public final class MutationSupport {
  private final Database db;

  public MutationSupport(Database db) {
    this.db = db;
  }

  public record Result(Envelope response, boolean replayed) {}

  public Result execute(String userId, Envelope request, Database.Work<Envelope> work) {
    String hash = hash(request.type(), request.payload());
    return db.transaction(
        c -> {
          try (PreparedStatement p =
              c.prepareStatement(
                  "SELECT request_hash,response_json FROM processed_requests WHERE user_id=? AND"
                      + " request_id=?")) {
            p.setString(1, userId);
            p.setString(2, request.requestId());
            try (ResultSet r = p.executeQuery()) {
              if (r.next()) {
                if (!hash.equals(r.getString(1)))
                  throw new DomainException(
                      IDEMPOTENCY_CONFLICT, "Request ID already used with different content");
                return new Result(Json.MAPPER.readValue(r.getString(2), Envelope.class), true);
              }
            }
          }
          Envelope response = work.run(c);
          if (!Boolean.TRUE.equals(response.ok()))
            throw new IllegalArgumentException("Mutation must return success or throw");
          try (PreparedStatement p =
              c.prepareStatement("INSERT INTO processed_requests VALUES(?,?,?,?,?)")) {
            p.setString(1, userId);
            p.setString(2, request.requestId());
            p.setString(3, hash);
            p.setString(4, Json.MAPPER.writeValueAsString(response));
            p.setString(5, java.time.Instant.now().toString());
            p.executeUpdate();
          }
          return new Result(response, false);
        });
  }

  public static void requireUpdated(int rows) {
    if (rows != 1)
      throw new DomainException(
          VERSION_CONFLICT, "Entity changed; refresh and review before retrying");
  }

  public static String hash(String type, JsonNode payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      Json.MAPPER.writeValueAsBytes(
                          canonical(
                              Json.tree(Map.of("type", type, "payload", canonical(payload)))))));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode canonical(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = Json.MAPPER.createObjectNode();
      TreeSet<String> names = new TreeSet<>();
      node.fieldNames().forEachRemaining(names::add);
      for (String name : names) result.set(name, canonical(node.get(name)));
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = Json.MAPPER.createArrayNode();
      node.forEach(n -> result.add(canonical(n)));
      return result;
    }
    return node;
  }
}
