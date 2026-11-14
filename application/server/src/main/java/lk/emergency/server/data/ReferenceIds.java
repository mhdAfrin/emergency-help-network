package lk.emergency.server.data;

import java.sql.*;
import lk.emergency.common.*;
import lk.emergency.common.Types.ErrorCode;

/** One durable global sequence: no duplicate references across accounts, teams or incidents. */
public final class ReferenceIds {
  private ReferenceIds() {}

  public static String next(Connection connection) throws SQLException {
    try (var statement =
            connection.prepareStatement(
                "UPDATE reference_sequence SET next_value=next_value+1 WHERE singleton=1 AND"
                    + " next_value<=99999 RETURNING next_value-1");
        var rows = statement.executeQuery()) {
      if (!rows.next())
        throw new DomainException(
            ErrorCode.VALIDATION_ERROR,
            "The five-digit reference range is full. Contact the project administrator.");
      return Integer.toString(rows.getInt(1));
    }
  }
}
