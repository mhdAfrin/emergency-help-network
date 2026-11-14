package lk.emergency.client;

/** Stable executable entry point; application code is grouped by responsibility. */
public final class ClientMain {
  private ClientMain() {}

  public static void main(String[] args) {
    lk.emergency.client.app.ClientApplication.main(args);
  }
}
