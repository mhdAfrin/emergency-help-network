package lk.emergency.common;

/** Production timings; tests inject shorter intervals without changing production defaults. */
public record NetworkTiming(long heartbeatMillis, long inactivityMillis, long writeTimeoutMillis) {
  public static final NetworkTiming DEFAULT = new NetworkTiming(15_000, 45_000, 5_000);

  public NetworkTiming {
    if (heartbeatMillis < 1 || inactivityMillis <= heartbeatMillis || writeTimeoutMillis < 1)
      throw new IllegalArgumentException("Invalid network timing");
  }
}
