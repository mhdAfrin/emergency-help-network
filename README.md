# Emergency Help Network

Java 21 console project for coordinating fictional Police, Ambulance and Fire & Rescue teams. Academic demonstration only; no real emergency dispatch.

This final edition starts from merged main d3c81ea and implements the user's simplified terminal interface. All three roles have seven main choices, readable result cards, short permanent numeric references and address-only reporting. The original brief is retained in docs/requirements/; the user's later UI/reference changes supersede its old coordinate and menu requirements.

## Run

1. Double-click **RUN-SERVER.cmd**. It builds if necessary, prepares demo accounts and starts the server. Leave that window open.
2. Double-click **RUN-CLIENT.cmd** once per user. Login as a different role in each client window. Press Enter for host 127.0.0.1 and port 5050.

| Role | Demo username | Demo password |
|---|---|---|
| Citizen | citizen1 or citizen2 | DemoPass123! |
| Operator | operator | DemoPass123! |
| Responder | police1, ambulance1, fire1 or fire2 | DemoPass123! |

The local ignored Java 21/tool cache supports this machine. On another machine install a Java 21 JDK and set JAVA_HOME. The project never requires a website or graphical toolkit.

## Main menus

| Choice | Citizen | Operator | Responder |
|---|---|---|---|
| 1 | Report an incident | Open reports | Current assignment |
| 2 | My open reports | Assign a team | Accept / decline |
| 3 | View report / progress | Manage incident | Next progress |
| 4 | History | Teams / availability | My team / availability |
| 5 | Account / saved actions | History / account | History / saved actions |
| 6 | Logout | Logout | Logout |
| 0 | Exit | Exit | Exit |

Operator Manage groups details, priority, closure, rejection and cancellation. Nothing required for coordination was removed. Ping and recovery work automatically; no diagnostic buttons are needed. Empty lists say "Nothing to show yet". Every prompt expects the answer it displays: a filter is a state/priority name, not another main-menu number.

Reports ask for category, address/landmark and description. No coordinate prompts or coordinate output appear. New accounts, teams, incidents, assignments and timeline rows receive permanent five-digit numeric references beginning at 10000. They are database IDs, not truncated UUIDs or temporary menu aliases. Message retry IDs remain internal; saved actions are selected by menu number.

## Build and verify

From this folder in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 build
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 smoke
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 load
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 package
```

See [actual results](docs/verification/RESULTS.md). Smoke uses real separate client/server processes and isolated test data; it does not leave an operator running for your manual session. Load measures localhost, not a separate-laptop LAN. Generated packages are under application/.local/submission/ and exclude tools, databases, journals and target output.

## Documentation

- [Setup and terminal display](docs/guides/SETUP.md)
- [Step-by-step demo](docs/guides/DEMO.md)
- [Code map: where to edit each feature](docs/guides/CODE-MAP.md)
- [Architecture](docs/design/ARCHITECTURE.md), [database and IDs](docs/design/DATABASE.md), [protocol](docs/design/PROTOCOL.md), [state rules](docs/design/STATE-MACHINES.md)
- [LAN and troubleshooting](docs/guides/LAN.md)

## Data and scope

This separate folder uses a fresh demo database. Earlier project folders and their user records are preserved. Do not copy an older live DB/journal into this edition without a migration plan: existing UUID references are intentionally not rewritten, because changing referenced records and saved responses could break uncertain-request reconciliation. V002 adds the new reference sequence while keeping V001 intact. The 10000-99999 range holds 90,000 global records, including timelines; exhaustion is refused explicitly.

The client never sends coordinates; optional legacy protocol/database fields remain for existing test/compatibility contracts and are hidden from the interface. Authorization, transactions, revisions, replay protection, service privacy, heartbeat, reconnect/re-login and snapshots remain active. TCP is for trusted fictional localhost/LAN demonstrations, without TLS or real agency connections. Separate-laptop LAN and physical terminal checks remain manual.

Source changes are uncommitted. No Git commit, push, merge or external upload is performed by this editing session. Use honest assisted-work/review attribution when publishing.
