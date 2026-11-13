
package lk.emergency.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ProcessedRequestRepository {

  public record ProcessedRequest(
    String userId,
    String requestId,
    String requestHash,
    String responseJson,
    String createdAt
  ) {
  }

  public ProcessedRequest find(
    Connection connection,
    String userId,
    String requestId
  ) throws SQLException {

    String sql = """
                SELECT user_id, request_id, request_hash,
                       response_json, created_at
                FROM processed_requests
                WHERE user_id = ? AND request_id = ?
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, userId);
      statement.setString(2, requestId);

      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return null;
        }

        return new ProcessedRequest(
          result.getString("user_id"),
          result.getString("request_id"),
          result.getString("request_hash"),
          result.getString("response_json"),
          result.getString("created_at")
        );
      }
    }
  }

  public void save(
    Connection connection,
    String userId,
    String requestId,
    String requestHash,
    String responseJson,
    String createdAt
  ) throws SQLException {

    String sql = """
                INSERT INTO processed_requests
                (user_id, request_id, request_hash,
                 response_json, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

    try (PreparedStatement statement =
           connection.prepareStatement(sql)) {

      statement.setString(1, userId);
      statement.setString(2, requestId);
      statement.setString(3, requestHash);
      statement.setString(4, responseJson);
      statement.setString(5, createdAt);

      statement.executeUpdate();
    }
  }
}
