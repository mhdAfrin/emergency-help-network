package lk.emergency.client.network;

import lk.emergency.client.recovery.*;

public final class ReconnectPolicy {
  private ReconnectPolicy() {}

  public static long delayMillis(int attempt, double random) {
    long base =
        switch (Math.min(attempt, 4)) {
          case 0 -> 1000;
          case 1 -> 2000;
          case 2 -> 4000;
          case 3 -> 8000;
          default -> 15000;
        };
    return Math.min(15000, Math.max(1, (long) (base * (0.8 + 0.4 * random))));
  }
}
