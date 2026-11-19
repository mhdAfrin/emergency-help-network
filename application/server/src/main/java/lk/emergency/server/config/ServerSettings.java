package lk.emergency.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** File settings with explicit command-line overrides for classroom LAN demonstrations. */
public record ServerSettings(String host, int port, int maxConnections, Path database, boolean seed) {
  public static final String HELP = """
      Emergency Help Network server (Java 21)
        --host ADDRESS          Bind interface (default 127.0.0.1; LAN: 0.0.0.0)
        --port NUMBER           TCP port, 1-65535 (default 5050)
        --database PATH         SQLite file (default .local/data/emergency.db)
        --max-connections N     Simultaneous sessions, 1-200 (default 32)
        --config PATH           Explicit properties file; missing file is an error
        --seed                  Prepare demo accounts without resetting existing records
        --help                  Show this help without opening a database
      Command-line values override config/server.properties (or legacy server.properties).
      Relative paths are resolved from the application working directory.
      Only one server owns the database. Clients connect using its LAN IP, never 0.0.0.0.
      """;

  public static ServerSettings load(String[] args) throws IOException {
    Map<String, String> overrides = new HashMap<>();
    boolean seed = false;
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.equals("--seed")) {
        seed = true;
        continue;
      }
      if (!java.util.Set.of("--host", "--port", "--database", "--max-connections", "--config").contains(key))
        throw new IllegalArgumentException("Unknown option: " + key + ". Use --help.");
      if (++i >= args.length || args[i].startsWith("--") || args[i].isBlank())
        throw new IllegalArgumentException("Missing value for " + key);
      if (overrides.putIfAbsent(key, args[i].strip()) != null)
        throw new IllegalArgumentException("Repeated option: " + key);
    }
    boolean explicit = overrides.containsKey("--config");
    Path file = Path.of(overrides.getOrDefault("--config", "config/server.properties"));
    if (!explicit && !Files.exists(file)) file = Path.of("server.properties");
    Properties config = new Properties();
    if (explicit && !Files.isRegularFile(file)) throw new IOException("Config file not found: " + file);
    if (Files.exists(file)) {
      try (InputStream in = Files.newInputStream(file)) {
        config.load(in);
      }
    }
    String host = overrides.getOrDefault("--host", config.getProperty("server.host", "127.0.0.1")).strip();
    if (host.isEmpty()) throw new IllegalArgumentException("Server bind address cannot be empty.");
    int port = number(overrides.getOrDefault("--port", config.getProperty("server.port", "5050")), "Port", 1, 65535);
    int limit = number(overrides.getOrDefault("--max-connections", config.getProperty("server.maxConnections", "32")), "Connection limit", 1, 200);
    String database = overrides.getOrDefault("--database", config.getProperty("database.path", ".local/data/emergency.db")).strip();
    if (database.isEmpty()) throw new IllegalArgumentException("Database path cannot be empty.");
    return new ServerSettings(host, port, limit, Path.of(database), seed);
  }

  private static int number(String value, String label, int minimum, int maximum) {
    try {
      int parsed = Integer.parseInt(value.strip());
      if (parsed >= minimum && parsed <= maximum) return parsed;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException(label + " must be " + minimum + "-" + maximum + ".");
  }
}
