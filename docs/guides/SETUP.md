# Setup

Use the repository root containing RUN-SERVER.cmd and application/. Java 21 is required. No Python is required to build or run this project.

Double-click RUN-SERVER.cmd once, then RUN-CLIENT.cmd in three separate windows for citizen1, operator and fire1. A client connects to the same server; use 127.0.0.1 and 5050 on this laptop. Demo password: DemoPass123!. Registering creates a citizen account only. Police/Ambulance/Fire are responder service types, not separate role implementations.

The launcher prepares demo accounts without resetting existing records, passwords or active reservations. Local data: application/.local/data/emergency.db; client drafts/pending actions: application/.local/client/. Stop with Ctrl+C or the client Exit option. Keep the database and journals for recovery; a network timeout is not proof a mutation failed.

PowerShell alternatives from repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 seed
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 server
```

In each separate client terminal:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\project.ps1 client
```

Output uses ASCII separators and UTF-8. To opt into colored headings in a terminal supporting ANSI (such as Windows Terminal), set `$env:EHN_COLOR='1'` before starting a client. Set NO_COLOR or leave EHN_COLOR unset for ordinary plain text; color is never required for understanding a result. Font face/size are controlled by the terminal app. Passwords are hidden in an interactive Java console; redirected test input may display a visible-password notice.

For Eclipse import application/ via Maven Existing Maven Projects, select JavaSE-21, and use application/ as the working directory. Entry points remain lk.emergency.server.ServerMain and lk.emergency.client.ClientMain. Source folders/packages remain normal Maven layout. Eclipse interaction has not been tested in this editing session.
