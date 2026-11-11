package lk.emergency.common;

import java.util.concurrent.*;

public final class OutboundQueue {
  private final ArrayBlockingQueue<Envelope> frames =
      new ArrayBlockingQueue<>(Validation.QUEUE_CAPACITY);

  public boolean offer(Envelope frame) {
    return frames.offer(frame);
  }

  public Envelope take() throws InterruptedException {
    return frames.take();
  }

  public int size() {
    return frames.size();
  }
}
