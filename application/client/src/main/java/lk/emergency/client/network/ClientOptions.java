package lk.emergency.client.network;

import java.util.HashMap;
import java.util.Map;

/** Optional endpoint arguments; interactive prompts remain available when omitted. */
public record ClientOptions(String host, Integer port, boolean check, boolean help) {
  public static final String HELP = """
      Emergency Help Network client (Java 21)
        --host ADDRESS    Server's reachable IPv4 address or hostname
        --port NUMBER     TCP port, 1-65535 (default 5050)
        --check           Check the actual EHN PING/PONG protocol, then exit
        --help            Show this help
      With no endpoint arguments, the usual host/port prompts are displayed.
      Example: java -jar client.jar --host 192.168.1.10 --port 5050
      127.0.0.1 means this laptop. Never use 0.0.0.0 as a client destination.
      """;

  public static ClientOptions parse(String[] args) {
    Map<String, String> values = new HashMap<>();
    boolean check = false, help = false;
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.equals("--check")) { check = true; continue; }
      if (key.equals("--help")) { help = true; continue; }
      if (!key.equals("--host") && !key.equals("--port"))
        throw new IllegalArgumentException("Unknown option: " + key + ". Use --help.");
      if (++i >= args.length || args[i].startsWith("--") || args[i].isBlank())
        throw new IllegalArgumentException("Missing value for " + key);
      if (values.putIfAbsent(key, args[i].strip()) != null)
        throw new IllegalArgumentException("Repeated option: " + key);
    }
    String host = values.containsKey("--host") ? validateHost(values.get("--host")) : null;
    Integer port = values.containsKey("--port") ? parsePort(values.get("--port")) : null;
    if (check && host == null && !help)
      throw new IllegalArgumentException("--check requires --host ADDRESS.");
    return new ClientOptions(host, port, check, help);
  }

  public static String validateHost(String text) {
    String host = text.strip();
    if (host.isBlank() || host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]"))
      throw new IllegalArgumentException("Enter the server's reachable IP or hostname, not a wildcard bind address.");
    if (host.contains("://") || host.contains("/") || host.chars().anyMatch(Character::isWhitespace))
      throw new IllegalArgumentException("Enter only a host/IP; enter the port separately, without http://.");
    return host;
  }

  public static int parsePort(String text) {
    try {
      int port = Integer.parseInt(text.strip());
      if (port >= 1 && port <= 65535) return port;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException("Server port must be 1-65535.");
  }
}
