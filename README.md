# Emergency Help Network Desktop UI

A simple Java Swing desktop edition of the finished Wi-Fi TCP project. Citizen, operator and responder dashboards use readable tables, selected-row actions, live updates and short numeric references. No browser or UI library dependency is required.

## Run

1. Open **RUN-SERVER.cmd** on the server laptop. In Server control choose the port and click **Start server**.
2. Open **RUN-CLIENT.cmd** for every client window. Enter the server laptop's Wi-Fi IP and matching port, click **Connect / check**, then sign in.
3. For a three-laptop demo: A runs server + operator, B runs citizen, C runs responder windows. With two laptops, B can host citizen and responder windows together.

The normal launchers use javaw and leave no terminal open. OPEN-SERVER.vbs / OPEN-CLIENT.vbs also avoid the brief CMD startup flash. RUN-LAN-* aliases open the same GUI. Development/build commands can use a terminal.

| Role | Username | Demo password |
|---|---|---|
| Citizen | citizen1 or citizen2 | DemoPass123! |
| Operator | operator | DemoPass123! |
| Responder | police1, ambulance1, fire1, fire2 | DemoPass123! |

Each window uses a different account. Citizen registration is available after connecting. Passwords are masked and not stored in endpoint settings.

## Laptop-ready files

- DEMO-PACKAGES/EHN-UI-SERVER-WINDOWS-X64.zip: GUI server + bundled Java runtime.
- DEMO-PACKAGES/EHN-UI-CLIENT-WINDOWS-X64.zip: GUI client + bundled Java runtime; send this to the other laptops.
- DEMO-PACKAGES/EHN-UI-SOURCE.zip: clean source/docs; requires a Java 21 JDK to build.

Extract the complete Windows x64 package into a writable folder. Do not run from inside a ZIP. Other architectures/operating systems need their own Java 21 installation. The portable runtime includes java.desktop for Swing and retains its license notices.

## Simple workflow

Citizen submits a report -> operator sees it live -> operator selects the report and assigns an online/available team -> responder accepts -> en route -> arrived -> completed -> operator closes the report -> citizen history. Multi-service assignments remain independent. Tables can be sorted; selecting a row keeps its real server reference. Extra operator actions sit under More actions. Details show authorized service progress and timelines.

The server alone owns SQLite. UI server data is application/.local/data/emergency-ui.db (portable package: .local/data/emergency-ui.db). Client journals/drafts are under .local/client-ui/. Older project folders/databases are preserved. Restart from the same folder/port, then re-login to restore authorized state. Saved actions reuse original request IDs for uncertain outcomes; new mutations are blocked until the pending action is reconciled.

## Guides and code

- [Tamil GUI setup and evaluation demo](docs/guides/UI-DEMO-TAMIL.md)
- [Network and firewall notes](docs/guides/WIFI-DEMO-TAMIL.md)
- [Actual UI verification and limitations](docs/verification/UI-RESULTS.md)
- [Code map](docs/guides/CODE-MAP.md), [architecture](docs/design/ARCHITECTURE.md), [protocol](docs/design/PROTOCOL.md)
- [Physical laptop rehearsal checklist](docs/guides/EVALUATION-CHECKLIST.md)

GUI code is grouped under common/.../desktop/, client/.../desktop/ and server/.../desktop/. Networking, authorization, transactional state rules and recovery remain in their existing packages. Socket/database work runs on background workers, with Swing updates on its event thread.

BUILD-PROJECT.cmd runs the clean regression suite. MAKE-DEMO-PACKAGES.cmd creates fresh GUI packages; rename existing ZIPs before regenerating them. Console entry points and scripts remain for automated checks/diagnostics. Earlier evaluation verification is historical baseline, not proof of this GUI edition.

Use a permitted Wi-Fi/hotspot that allows device-to-device TCP. 127.0.0.1 means the current laptop, while 0.0.0.0 is a server bind address. Optional server-only ALLOW-LAN-FIREWALL.cmd requires administrator action on a trusted Private network. The fictional academic protocol has no TLS and contacts no real agencies. Cross-laptop Wi-Fi/firewall behavior still needs the group's rehearsal.
