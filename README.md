# Emergency Help Network

**Police · Ambulance · Fire & Rescue**

University of Ruhuna, Faculty of Engineering · EC5205 — Operating Systems & Network Programming

> Academic demonstration — no real emergency dispatch.

The complete Java 21 console prototype implements reporting, multi-service offers, responder acceptance/decline, independent progress, reassignment, eligible closure, persistent public/private history and disconnect recovery. It uses real TCP sockets, bounded platform threads, framed JSON and server-owned SQLite.

## Build and run

From this checkout folder in PowerShell:

```powershell
.\application\scripts\project.ps1 build
.\application\scripts\project.ps1 seed
.\application\scripts\project.ps1 server
```

In a separate terminal for each citizen/operator/team:

```powershell
.\application\scripts\project.ps1 client
```

The helper uses the local validation JDK when present, otherwise an installed Java 21 JDK via JAVA_HOME/PATH. First build needs internet for the pinned Maven wrapper/dependencies; with a complete cache use `build -Offline`.

Default connection: **127.0.0.1:5050**. Demo password **DemoPass123!** for operator, citizen1, citizen2, police1, ambulance1, fire1 and fire2. Citizen registration also works. Seeding is explicit, repeatable and does not reset saved accounts.

For custom host/port/DB/session limits copy application/config/server.properties.example to application/config/server.properties. Manual JAR/Eclipse launches use **application/** as working directory. Clients never access SQLite. The default DB is application/.local/data/emergency.db; local per-server/user draft and pending-request journals are in application/.local/client/.

## Demonstration

Report → notify operator → set priority → offer several services → accept/decline → replace declined/cancelled offers → EN_ROUTE → ARRIVED → COMPLETED independently → close when all assignments are terminal and at least one completed.

Clients send heartbeat every 15 seconds and detect 45 seconds without inbound frames. Reconnect uses 1/2/4/8/up to 15 seconds with jitter, requires login again and synchronizes an authorized snapshot before applying newer buffered events. BUSY teams remain reserved through disconnect/restart. Report drafts and original uncertain request IDs survive client restart. Menu 90 explicitly reconciles/retries saved requests; it never silently invents a new report ID.

## Verification and delivery

```powershell
.\application\scripts\project.ps1 smoke
.\application\scripts\project.ps1 load
.\application\scripts\project.ps1 package
```

Smoke uses a real server and six separate console client JVMs, server restart/re-login, closure/history and draft recovery. Load uses 20 authenticated real TCP sessions and measures 50 commit-to-operator-receipt samples with one monotonic clock. Package creates source and runnable JAR ZIPs under ignored application/.local/submission/.

Actual results, including failed intermediate attempts and manual limits, are in [the test report](docs/verification/TEST-RESULTS.md). Separate-laptop LAN, physical keyboard/terminal behavior, Eclipse interaction and lecturer-specific submission requirements must be checked by the group; no claim is made they were performed automatically.

## Documentation

- [Documentation index](docs/README.md)
- [Architecture/recovery sequence](docs/design/ARCHITECTURE.md), [database](docs/design/DATABASE.md), [protocol](docs/design/PROTOCOL.md), [state diagrams](docs/design/STATE-MACHINES.md)
- [Setup/Eclipse/LAN](docs/guides/SETUP.md), [complete demo](docs/guides/DEMO.md), [troubleshooting and limits](docs/guides/TROUBLESHOOTING.md)
- [All 29 Member 3 tasks](docs/handoff/MEMBER3-CHECKLIST.md), [final handoff](docs/handoff/HANDOFF.md), [actual contributions](docs/handoff/CONTRIBUTIONS.md)
- [Acceptance-test traceability](docs/verification/TRACEABILITY.md), [LAN checklist](docs/verification/LAN-CHECKLIST.md), [submission checklist](docs/submission/CHECKLIST.md)

## Repository and scope

```text
member3-finalized/
├── application/   common/, server/, client/, config/, scripts/, Maven wrapper and build
├── docs/          Requirements, design, verification evidence and delivery guides
├── .gitignore
└── README.md
```

Local JDK/caches/journals/DBs, module target/ directories and generated submission ZIPs are ignored. This independent checkout is on **member3-finalization**, based on Member 2 commit **e858384**. Other member folders are preserved; no commit, push or merge is performed automatically. Merge/review Member 2 before merging this stage into main.

This is a fictional trusted-localhost/LAN prototype. TCP is unencrypted; no real agencies, production hosting, arrival guarantees or third-party dispatch services are involved.
