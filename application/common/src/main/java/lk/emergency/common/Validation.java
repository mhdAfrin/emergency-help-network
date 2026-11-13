package lk.emergency.common;

import static lk.emergency.common.Types.ErrorCode.*;

import com.fasterxml.jackson.databind.JsonNode;

public final class Validation {
  public static final int USERNAME_MIN = 3,
      USERNAME_MAX = 40,
      DISPLAY_NAME_MAX = 80,
      PASSWORD_MIN = 8,
      PASSWORD_MAX = 128,
      CONTACT_MAX = 40,
      ADDRESS_MAX = 200,
      DESCRIPTION_MAX = 1000,
      NOTE_MAX = 500,
      MAX_FRAME = 65536,
      QUEUE_CAPACITY = 100;
  public static final String DISCLAIMER = "Academic demonstration — no real emergency dispatch.";

  private Validation() {}

  public static String text(String value, int min, int max, String field) {
    String result = value == null ? "" : value.strip();
    int length = result.codePointCount(0, result.length());
    if (length < min || length > max)
      throw new DomainException(
          VALIDATION_ERROR, field + " must contain " + min + "–" + max + " characters");
    return result;
  }

  public static String password(String value) {
    if (value == null || value.length() < PASSWORD_MIN || value.length() > PASSWORD_MAX)
      throw new DomainException(VALIDATION_ERROR, "Password must contain 8–128 characters");
    return value;
  }

  public static <T> T payload(JsonNode node, Class<T> type) {
    if (node == null || !node.isObject())
      throw new DomainException(VALIDATION_ERROR, "Payload must be an object");
    try {
      return Json.MAPPER.treeToValue(node, type);
    } catch (Exception e) {
      throw new DomainException(VALIDATION_ERROR, "Invalid or unknown payload fields");
    }
  }

  public static void empty(JsonNode node) {
    if (node == null || !node.isObject() || !node.isEmpty())
      throw new DomainException(VALIDATION_ERROR, "Expected empty payload");
  }

  public static long revision(Long revision) {
    if (revision == null || revision < 0)
      throw new DomainException(
          VALIDATION_ERROR, "An explicit nonnegative expected revision is required");
    return revision;
  }

  public static int offset(Integer value) {
    if (value != null && value < 0)
      throw new DomainException(VALIDATION_ERROR, "Offset cannot be negative");
    return value == null ? 0 : value;
  }

  public static int limit(Integer value) {
    if (value != null && (value < 1 || value > 5))
      throw new DomainException(VALIDATION_ERROR, "Limit must be 1–5");
    return value == null ? 5 : value;
  }
}
