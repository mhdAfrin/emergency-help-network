
package lk.emergency.server;

import java.sql.Connection;
import java.time.Instant;

import lk.emergency.common.DomainException;
import lk.emergency.common.Envelope;
import lk.emergency.common.Json;

import static lk.emergency.common.Types.ErrorCode;

public final class IdempotencyService {

  @FunctionalInterface
  public interface Operation {
    Envelope execute(Connection connection) throws Exception;
  }

  private final Database database;

  private final ProcessedRequestRepository repository =
    new ProcessedRequestRepository();

  public IdempotencyService(Database database) {
    this.database = database;
  }

  public Envelope execute(
    String userId,
    Envelope request,
    Operation operation) {

    String fingerprint = RequestFingerprint.calculate(request);

    return database.transaction(connection -> {

      ProcessedRequestRepository.ProcessedRequest previous =
        repository.find(
          connection,
          userId,
          request.requestId()
        );

      if (previous != null) {

        if (!previous.requestHash().equals(fingerprint)) {
          throw new DomainException(
            ErrorCode.IDEMPOTENCY_CONFLICT,
            "Request ID was already used with different data"
          );
        }

        return Json.MAPPER.readValue(
          previous.responseJson(),
          Envelope.class
        );
      }

      Envelope response = operation.execute(connection);

      repository.save(
        connection,
        userId,
        request.requestId(),
        fingerprint,
        Json.MAPPER.writeValueAsString(response),
        Instant.now().toString()
      );

      return response;
    });
  }
}
