# Emergency Help Network

**Police · Ambulance · Fire & Rescue**

University of Ruhuna, Faculty of Engineering · EC5205 — Operating Systems & Network Programming

> Academic demonstration — no real emergency dispatch.

Member 1's foundation and Member 2's reporting/assignment stage are implemented in this working copy. The original Member 2 snapshot is commit `8bdf413`; corrections remain on its existing branch, `member2-submission-snapshot`, ready for review. No commit or push is performed automatically.

## What works

- Citizen registration/login, single-session replacement, and server-side role/ownership checks.
- Fictional incident reports, confirmed references, paginated listings, public timelines and separate service assignment views.
- Operator state/priority filters, priority changes, team filters, and eligible public rejection.
- Online/available team offers for three services on one incident; atomic BUSY reservations and revision checks.
- Responder availability controls and private inspection of its own offers/current assignment.
- Eligible cancellation with a private reason, reservation release, and reassignment.
- Live authorized notifications after commit; canonical same-ID retries with no duplicate mutations or events.
- A Pending actions menu retains timed-out business request IDs and input for same-session reconciliation/retry.

Acceptance/decline, responder progress, closure, full histories, heartbeat, automatic reconnect and snapshot synchronization remain Member 3 work. These actions are not exposed as working controls.

## Build and run

From this folder in PowerShell, with Java 21 available:

```powershell
.\application\scripts\project.ps1 build
.\application\scripts\project.ps1 smoke
.\application\scripts\project.ps1 seed
.\application\scripts\project.ps1 server
```

Use a separate terminal for every client:

```powershell
.\application\scripts\project.ps1 client
```

The helper uses `.local/tools/jdk-21.0.13+9` inside application/ when available, otherwise JAVA_HOME/Java on PATH. The local toolchain and caches are optional and ignored by Git. With a complete dependency cache, `build -Offline` avoids network downloads. A fresh clone requires Java 21 and internet for Maven/dependencies.

Default address: **127.0.0.1:5050**. Demo password: `DemoPass123!` for `operator`, `citizen1`, `citizen2`, `police1`, `ambulance1`, `fire1`, `fire2`. Seed is explicit and repeatable; it never resets saved passwords or availability.

For custom settings copy `application/config/server.properties.example` to `application/config/server.properties`. The default database is `application/.local/data/emergency.db`. Run manual JAR/Eclipse launches with **application/** as the working directory. Clients never access SQLite.

## Folder layout

```text
member2-fixed/
├── application/   Maven modules, configuration, scripts and ignored local runtime files
├── docs/          Requirements, design, guides, handoff and verification evidence
├── .gitignore
└── README.md
```

application/ contains client/, common/, server/, config/, scripts/, the parent POM and Maven wrapper. Module target/ output, local databases, downloaded tools and private configuration stay out of Git.

## Read next

- [Documentation index](docs/README.md)
- [All 26 Member 2 tasks](docs/handoff/MEMBER2-CHECKLIST.md)
- [Protocol and payloads](docs/design/PROTOCOL.md)
- [Current test results](docs/verification/TEST-RESULTS.md)
- [Demo and manual checks](docs/guides/DEMO.md)
- [Member 3 handoff](docs/handoff/HANDOFF.md)
- [Review and push on Member 2's branch](docs/handoff/PUSH-MEMBER2.md)
- [Eclipse, direct commands and LAN troubleshooting](docs/guides/SETUP.md)

Use fictional data on localhost or a permitted trusted LAN. TCP is unencrypted. Separate-laptop LAN behavior and interactive typing during notifications still need manual verification. Console events print independently of input, with prompt redraw and Asia/Colombo timestamps. No heartbeat/inactivity deadline or automatic reconnect is implemented at this stage.
