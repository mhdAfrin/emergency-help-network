# Emergency Help Network — Member 1 foundation

University of Ruhuna · Faculty of Engineering · EC5205 Operating Systems & Network Programming.
Academic demonstration — no real emergency dispatch.

Member 1 implements networking, SQLite, authentication and reusable transaction foundations. Incident reporting/assignment/progress are deliberately reserved for the next sequential members. See docs/handoff/MEMBER1-CHECKLIST.md and docs/handoff/HANDOFF.md.

## Build and run (PowerShell)

Use a Java 21 JDK. Set JAVA_HOME to its installation directory and put its bin first in PATH. First build needs internet for pinned dependencies and the Maven 3.9.9 wrapper; later builds can use cached dependencies. Open PowerShell in application/ (the Maven working directory). Use a UTF-8 console (`chcp 65001` in Windows Terminal if needed).

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd clean verify
Copy-Item config/server.properties.example config/server.properties
java -jar server/target/server-1.0.0-SNAPSHOT.jar --seed
java -jar server/target/server-1.0.0-SNAPSHOT.jar
```

In each separate terminal:

```powershell
java -jar client/target/client-1.0.0-SNAPSHOT.jar
```

Enter host 127.0.0.1 and port 5050 (or press Enter for defaults). Seed users: operator, citizen1, citizen2, police1, ambulance1, fire1, fire2. Every demo password is DemoPass123!. Registration also works without seeding. Seed must be run explicitly; startup does not silently create accounts. Data lives in .local/data/emergency.db relative to the server's working directory and survives restarts. Never run two servers on the same DB.

The optional .local/tools/ folder on this machine contains a checksum-verified Java 21 JDK used for validation; it is ignored by Git and is not a required source deliverable. In a restricted environment, `-Dmaven.repo.local=...` and MAVEN_USER_HOME can point to writable caches. docs/verification/TEST-RESULTS.md records the exact validation commands here.

## What to try

Log in to several separate clients. Citizen: identity, PING, logout, citizen registration from startup. Operator: list teams including connection presence; private test event while the socket reader runs independently. Responder: own team only. Logging the same user into another client notifies and closes the previous session. Use Ping within 60 seconds during this foundation demo; automated heartbeat/reconnect is Member 3 work.

Default connection limit 32; per-session outgoing limit 100 frames; max payload 65,536 bytes. config/server.properties can change host, port, maxConnections (1–200), and database path. All application logic is Java; no browser or GUI.

## Eclipse

1. Install/configure a Java 21 JDK in Window → Preferences → Java → Installed JREs and JavaSE-21 execution environment.
2. File → Import → Maven → Existing Maven Projects; select application/ and import parent/common/server/client. Maven → Update Project if dependencies need refreshing.
3. Build once with the wrapper. Create a Java Application launch for `lk.emergency.server.ServerMain` in server. Set working directory to application/. Run with argument `--seed` once, then remove it and launch server.
4. Create a Java Application launch for `lk.emergency.client.ClientMain` in client, also with application/ as working directory. Allow multiple instances and run it once per person/team; Eclipse console selector switches among them. Console encoding UTF-8; passwords are visible in Eclipse because System.console() is absent.
5. Use Terminate/Ctrl+C to stop clients/server; tests exercise explicit graceful close. Do not use Eclipse auto-generated module-info.java: this project uses the ordinary classpath.

Eclipse steps are supplied, not interactively verified in the IDE during this session.

## LAN and troubleshooting

127.0.0.1 reaches this machine only. For LAN, bind the server to 0.0.0.0 in config/server.properties and connect remote clients to the server laptop's LAN IP (find with ipconfig). 0.0.0.0 is a bind address, never a client destination. Use a permitted trusted LAN with fictional data; TCP is unencrypted. Check matching ports, server process, Windows firewall inbound permission and Wi-Fi device isolation. No automatic firewall changes are made. Public hosting/TLS is outside scope. Separate-laptop LAN verification remains a group test.

If bind fails, another server may own the port. If login fails, run --seed using the same working directory/config as the server. If SQLite is locked, check for a second server or external DB tool. If Maven cannot write .m2, choose a writable local repository; do not delete another project's cache. If the launcher hangs, use a real JDK bin path rather than a broken system Java shim.

## Documentation

- docs/design/ARCHITECTURE.md — sockets, threads, lifecycle and security boundaries
- docs/design/DATABASE.md — schema and ER diagram
- docs/design/PROTOCOL.md — envelopes, authorization, errors and extension contract
- docs/verification/TEST-RESULTS.md — actual outcomes, measurements and untested items
- docs/guides/DEMO.md — Member 1 demonstration script
- docs/handoff/MEMBER1-CHECKLIST.md — all 25 tasks with evidence
- docs/handoff/HANDOFF.md — next-member extension points and backlog
- docs/handoff/CONTRIBUTIONS.md — honest contribution and Git workflow

For source handoff/submission, exclude .local/, target/ and local configuration; .local/tools/ contains large local JDK/build caches. Keep the Maven wrapper, source, POMs and docs. The wrapper is Apache-generated with one Windows quoting fix for cache paths containing spaces; its distribution checksum is pinned.

No commits, pushes, PRs or fictitious member identities were created. Initialize the shared repository under the group's real accounts, review Member 1 work, then start Member 2 from the merged result.
