package lk.emergency.common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FrameCodecTest {
  private Envelope request() {
    return Envelope.request("PING", UUID.randomUUID().toString(), Map.of("unicode", "ආයුබෝවන් 🚑"));
  }

  private byte[] encoded(Envelope frame) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FrameCodec.write(out, frame);
    return out.toByteArray();
  }

  @Test
  void fragmentedAndCombinedFrames() throws Exception {
    Envelope frame = request();
    byte[] bytes = encoded(frame);
    ByteArrayOutputStream both = new ByteArrayOutputStream();
    both.write(bytes);
    both.write(bytes);
    InputStream fragmented =
        new FilterInputStream(new ByteArrayInputStream(both.toByteArray())) {
          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, Math.min(1, len));
          }
        };
    assertEquals(frame, FrameCodec.read(fragmented));
    assertEquals(frame, FrameCodec.read(fragmented));
    assertNull(FrameCodec.read(fragmented));
  }

  @Test
  void invalidLengthsRejectedBeforeAllocation() {
    for (int n : new int[] {0, -1, 65537, Integer.MAX_VALUE})
      assertThrows(
          IOException.class,
          () -> {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new DataOutputStream(b).writeInt(n);
            FrameCodec.read(new ByteArrayInputStream(b.toByteArray()));
          });
  }

  @Test
  void interruptedFrames() throws Exception {
    byte[] bytes = encoded(request());
    for (int length : new int[] {1, 2, 3, 4, bytes.length - 1})
      assertThrows(
          EOFException.class,
          () -> FrameCodec.read(new ByteArrayInputStream(Arrays.copyOf(bytes, length))));
  }

  @Test
  void malformedJsonAndUtf8() {
    for (byte[] payload :
        new byte[][] {
          "{".getBytes(), new byte[] {(byte) 0xc3, 0x28}, "null".getBytes(), "{} {}".getBytes()
        }) {
      assertThrows(
          IOException.class,
          () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new DataOutputStream(out).writeInt(payload.length);
            out.write(payload);
            Envelope result = FrameCodec.read(new ByteArrayInputStream(out.toByteArray()));
            if (result == null) throw new IOException("Null envelope");
          });
    }
  }

  @Test
  void queueBoundIsExactly100() throws Exception {
    OutboundQueue queue = new OutboundQueue();
    for (int i = 0; i < 100; i++) assertTrue(queue.offer(request()));
    assertEquals(100, queue.size());
    assertFalse(queue.offer(request()));
    queue.take();
    assertTrue(queue.offer(request()));
  }

  @Test
  void maximumPayloadAndOversizeWrite() throws Exception {
    Envelope frame = request();
    byte[] small = encoded(frame);
    int base = small.length - 4;
    String json = Json.MAPPER.writeValueAsString(frame);
    byte[] atLimit =
        (json + " ".repeat(65536 - base)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new DataOutputStream(out).writeInt(atLimit.length);
    out.write(atLimit);
    assertEquals(frame, FrameCodec.read(new ByteArrayInputStream(out.toByteArray())));
    assertThrows(
        IOException.class,
        () ->
            encoded(
                Envelope.request(
                    "PING", UUID.randomUUID().toString(), Map.of("x", "x".repeat(65536)))));
  }
}
