package lk.emergency.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;

public final class Json {
  private Json() {}

  public static final ObjectMapper MAPPER =
      new ObjectMapper(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  public static JsonNode tree(Object value) {
    return MAPPER.valueToTree(value);
  }
}
