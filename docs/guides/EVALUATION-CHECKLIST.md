# Laptop demo rehearsal record

Fill this from the actual physical-laptop rehearsal. Blank rows are NOT passed tests.

Date/time (Asia/Colombo): ______  Tester(s): ______  Shared Wi-Fi/hotspot: ______

| Machine | OS / architecture | Role windows | Actual IPv4 | Port / version |
|---|---|---|---|---|
| Laptop A | | Server + operator | | |
| Laptop B | | Citizen | | |
| Laptop C (optional) | | Police / Ambulance / Fire | | |

| Check | Observed result / evidence | Pass/fail |
|---|---|---|
| Only A runs a server; all laptops use the same endpoint | | |
| Client package runs after full extraction, with bundled Java | | |
| CHECK-CONNECTION returns EHN PONG from each remote laptop | | |
| Distinct accounts log in simultaneously | | |
| Citizen reports, operator receives while idle | | |
| Team ONLINE + AVAILABLE, offer reaches correct responder | | |
| Accept -> EN_ROUTE -> ARRIVED -> COMPLETED | | |
| Citizen sees only authorized public progress | | |
| Police/Ambulance/Fire assignments progress independently (if demonstrated) | | |
| Operator closure eligibility and citizen history | | |
| Server restart, clients reconnect and re-login, current state restored | | |
| All local draft / pending action recovery used without duplicate mutations | | |
| Server restarts using same DB; no lost history | | |
| Optional demo firewall rule removed when no longer needed | | |

Explain: one central database, persistent framed TCP connections, separate read/write paths, background events, authentication + role authorization, transactional updates, short IDs, reconnect with login and authorized snapshots. The firewall helper is optional and requires owner/admin action. Same Wi-Fi SSID alone does not establish reachability if the access point isolates clients.
