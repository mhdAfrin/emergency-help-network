# Emergency Help Network

**Police · Ambulance · Fire & Rescue**

University of Ruhuna, Faculty of Engineering · EC5205 — Operating Systems & Network Programming

> Academic demonstration — no real emergency dispatch.

## Project folders

```text
project/
├── application/   Complete Java application, build tools and local runtime files
├── docs/          Requirements, design, guides, handoff and verification evidence
├── .gitignore     Excludes local tools, databases and generated build files
└── README.md      Start here
```

All implementation files are inside **application/**. It contains the Maven modules (`client`, `common`, `server`), configuration examples, scripts, wrapper and parent POM. Its `.local/` directory holds the existing database and downloaded tools and is excluded from Git. Each module's generated `target/` directory is also excluded.

## Build and run

Use Java 21. From this project folder in PowerShell:

```powershell
.\application\scripts\project.ps1 build
.\application\scripts\project.ps1 seed
.\application\scripts\project.ps1 server
```

Start each client in a separate terminal:

```powershell
.\application\scripts\project.ps1 client
```

The helper selects the existing local Java 21 JDK when available and runs commands from `application/` automatically. For a fresh checkout, install a Java 21 JDK and set JAVA_HOME. The first build needs internet access.

Connect to **127.0.0.1:5050**. Demo accounts: `operator`, `citizen1`, `citizen2`, `police1`, `ambulance1`, `fire1`, `fire2`. Password: `DemoPass123!`.

Custom configuration: copy `application/config/server.properties.example` to `application/config/server.properties`. Existing records are preserved in `application/.local/data/emergency.db`.

## Documentation and verification

- [Documentation index](docs/README.md)
- [Setup, Eclipse and LAN instructions](docs/guides/SETUP.md)
- [Architecture](docs/design/ARCHITECTURE.md), [database](docs/design/DATABASE.md), [protocol](docs/design/PROTOCOL.md)
- [Member 1 checklist](docs/handoff/MEMBER1-CHECKLIST.md) and [handoff](docs/handoff/HANDOFF.md)
- [Test results](docs/verification/TEST-RESULTS.md)
- [Folder layout](docs/guides/REPOSITORY-LAYOUT.md)

Run the separate-process verification after building:

```powershell
.\application\scripts\project.ps1 smoke
```

Member 1's networking, database and authentication foundation is implemented. Reporting, assignment, responder progress and recovery remain Members 2/3 work. Use Ping before the current 60-second socket timeout; automatic heartbeat/reconnect belongs to Member 3. Eclipse GUI operation and separate-laptop LAN checks remain manual follow-ups.

No commit or push has been performed.
