package lk.emergency.common;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

public final class FrameCodec {
  private FrameCodec() {}

  public static Envelope read(InputStream in) throws IOException {
    int first = in.read();
    if (first < 0) return null;
    byte[] rest = in.readNBytes(3);
    if (rest.length != 3) throw new EOFException("Interrupted prefix");
    int length = (first << 24) | ((rest[0] & 255) << 16) | ((rest[1] & 255) << 8) | (rest[2] & 255);
    if (length < 1 || length > Validation.MAX_FRAME) throw new IOException("Invalid frame length");
    byte[] payload = in.readNBytes(length);
    if (payload.length != length) throw new EOFException("Interrupted payload");
    try {
      String json =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(payload))
              .toString();
      Envelope envelope = Json.MAPPER.readValue(json, Envelope.class);
      if (envelope == null) throw new IOException("Null envelope");
      return envelope;
    } catch (Exception e) {
      throw new IOException("Malformed JSON envelope", e);
    }
  }

  public static void write(OutputStream out, Envelope frame) throws IOException {
    byte[] bytes = Json.MAPPER.writeValueAsBytes(frame);
    if (bytes.length < 1 || bytes.length > Validation.MAX_FRAME)
      throw new IOException("Frame too large");
    DataOutputStream data = new DataOutputStream(out);
    data.writeInt(bytes.length);
    data.write(bytes);
    data.flush();
  }
}
