
package lk.emergency.server;

import com.fasterxml.jackson.databind.JsonNode;
import lk.emergency.common.Envelope;
import lk.emergency.common.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprint {

  private RequestFingerprint() {
  }

  public static String calculate(Envelope request) {
    try {
      MessageDigest digest =
        MessageDigest.getInstance("SHA-256");

      digest.update(
        request.type().getBytes(StandardCharsets.UTF_8)
      );

      digest.update((byte) 0);

      JsonNode payload = request.payload();

      String payloadJson = payload == null
        ? "null"
        : Json.MAPPER.writeValueAsString(payload);

      digest.update(
        payloadJson.getBytes(StandardCharsets.UTF_8)
      );

      return HexFormat.of().formatHex(digest.digest());

    } catch (NoSuchAlgorithmException |
             com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(
        "Unable to calculate request fingerprint", e
      );
    }
  }
}
