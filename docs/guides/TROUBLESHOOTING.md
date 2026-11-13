# Troubleshooting and limits

Use the helper from the checkout root, or application/ as the working directory for direct JAR/Eclipse launches. Java must be a full Java 21 JDK for builds/source-launch checks; runtime JARs need Java 21.

| Symptom | Check/action |
|---|---|
| Maven says no such host repo.maven.apache.org | DNS/network failed before compiling. With a complete cached dependency set use build -Offline; otherwise restore permitted network/DNS and retry. Smoke needs a successful build/JARs first. |
| JAVA_HOME path missing | Use the installed JDK or local validation JDK inside this checkout. Do not reuse an old moved member-folder path. |
| Server bind fails | Another process may use the port. Stop it or change server.port in config/server.properties. |
| Login missing demo users | Run seed from the same working directory/config as the server. Seed never resets existing passwords. |
| SQLite locked | Stop a second server/external DB editor. One server owns the file; foreign keys/WAL/5-second busy timeout are enabled. |
| Team unavailable | It must be connected and AVAILABLE; active assignments reserve BUSY even offline. Read current team state and review. |
| VERSION_CONFLICT | Refresh all affected revisions and review the changed action. Do not silently edit an uncertain request's payload and reuse its ID. |
| Confirmation pending/disconnect | Preserve original input/ID. Reconnect, login, then menu 90 to reconcile/retry unchanged. A network error does not prove rollback. |
| Reconnected but actions fail | Login again and wait for authorized snapshot synchronization. Passwords are not cached/auto-replayed. |
| Another login replaced this session | The newer login owns the account. This client may reconnect anonymously; only an explicit login can replace it again. |
| Snapshot expired/overflowed | Fetch a fresh synchronization after login. Tokens last 30s; the event buffer is 1,000 and active snapshot 5,000 records. No silent truncation is accepted. |
| Closed/declined work missing from active view | Use citizen/operator History or responder History/assignment timeline. Snapshots synchronize active state; history is independently paginated. |
| Private note missing for citizen | Intentional role projection. Operators see operational notes; responders see only their own notes; citizens see public timeline/messages. |
| Partially typed console line looks split | Notifications redraw the prompt, but ordinary terminal echo can mix typed text. Use UTF-8 Windows Terminal/Eclipse and verify physical typing behavior on the demo setup. |

127.0.0.1 reaches the same machine. 0.0.0.0 is a server bind address, not a client destination. Remote clients use the server laptop's LAN IP. A permitted LAN can still block peers through firewall/device isolation; see ../verification/LAN-CHECKLIST.md. Do not automatically alter firewall/network settings.

This academic prototype uses plaintext TCP and fictional data on a trusted machine/LAN. It has no production TLS provisioning, real dispatch, rate-limiting/account recovery, persistent offline event journal or exactly-once network guarantee. Per-session queues, caches, pending journals and snapshots are bounded. Local journals are unencrypted fictional input under the OS user profile and should be preserved for reconciliation. Old noncanonical Member 2 snapshot DBs are not silently imported; use the matching rebuilt client/server and keep old user records backed up.

Timeout tests can fail if the host is suspended or heavily stalled during a timed test. Preserve the failed log and rerun the isolated case, then the full suite; never label that failed run as passing.
