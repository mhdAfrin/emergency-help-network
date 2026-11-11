# Server configuration

Copy server.properties.example to server.properties inside this directory. Run the server from the repository root (the helper script does this automatically).

| Property | Default | Purpose |
|---|---|---|
| server.host | 127.0.0.1 | Server bind address; use 0.0.0.0 only for a permitted trusted LAN |
| server.port | 5050 | TCP listening port |
| server.maxConnections | 32 | Simultaneous session limit, 1–200 |
| database.path | .local/data/emergency.db | Persistent SQLite file, relative to working directory |

Remote clients use the server laptop's LAN IP, never 0.0.0.0. The local server.properties is ignored by Git. If absent, the server also accepts the previous root-level server.properties before using defaults.
