# Actual verification - simplified final edition

14 November 2026, Asia/Colombo. Separate checkout EMERGENCY-HELP-NETWORK-FINAL; local branch console-simplification; based on merged main d3c81ea. No commit/push/merge performed by this editing session.

## Executed results

**Clean Maven verification passed: 92 tests, zero failures/errors/skips**, Java 21.0.13+9-LTS, Maven 3.9.9. Full run finished at 10:45:01 +05:30, 3 minutes 42 seconds. [Build log](current/build.txt), [parsed suite summary](current/suite-summary.txt).

| Module | Suite | Tests |
|---|---|---:|
| common | FrameCodecTest | 6 |
| common | ValidationTest | 12 |
| server | DatabaseMechanismsTest | 3 |
| server | FinalWorkflowTest | 13 |
| server | FoundationIntegrationTest | 7 |
| server | IncidentValidationTest | 4 |
| server | Member2IntegrationTest | 20 |
| server | RevisionTransactionTest | 2 |
| server | ShortReferenceTest | 2 |
| server | SlowWriterTest | 2 |
| client | ConnectionManagerTest | 1 |
| client | HeartbeatTest | 2 |
| client | LiveStateTest | 4 |
| client | LocalJournalTest | 4 |
| client | PendingActionsTest | 3 |
| client | RecoveryIntegrationTest | 4 |
| client | PresentationTest | 3 |
| Total | | 92 |

The 87 existing regressions remain, plus five focused tests: three presentation/journal tests and two short-reference tests. Actual new checks cover seven main choices for every role, useful card fields without JSON/coordinates/revisions, numeric-user durable drafts/pending requests, five-digit global uniqueness, persistence across reopen, concurrent allocation and explicit range exhaustion. Business validation/privacy/ownership/revisions/dedup/heartbeat/snapshot tests remain intact under their reorganized packages.

**Actual six-client process smoke passed** using packaged JARs: report/live notification, three service offers, decline/replacement, acceptance, independent progress, eligible closure/history, server restart/background reconnect/explicit re-login/BUSY restoration, fresh-process closed history and coordinate-free draft recovery. [Result](current/smoke.txt), [actual role logs](current/process-smoke/citizen1.log). All console logs were checked for raw JSON ID dumps, UUIDs, coordinate prompts and mojibake: none were displayed.

**20 authenticated real TCP sessions and 500 PING exchanges passed.** Fifty CREATE_INCIDENT notifications measured from immediately after JDBC commit to the operator background callback after decoding the event, using real loopback sockets in one JVM/common monotonic clock. Median **1.297750 ms**, p95 nearest-rank **4.102700 ms**. [Method/environment/result](current/load.txt), [raw CSV](current/load-latency.csv). Windows 11 10.0 amd64; 16 available processors. This excludes DB-write time and physical terminal repaint and is not a separate-laptop LAN benchmark.

**Both actual Windows launchers passed** in a temporary isolated copy: RUN-SERVER.cmd seeded/started; RUN-CLIENT.cmd connected, logged in with a five-digit account, displayed the citizen menu and logged out. [Launcher check](current/launchers.txt). Optional ANSI colors were not claimed visually tested; plain ASCII output and UTF-8 launch handling were tested.

One initial build had a six-second socket-read timeout in a workflow fixture; its log is preserved under ignored application/.local/logs/initial-build-timeout.txt, not counted as passing. The full rerun passed all 92 cases. The test peer budget was then aligned to ten seconds and the complete 13-case workflow suite passed again ([recheck](current/workflow-recheck.txt), finished 10:48:06 +05:30). No production heartbeat/inactivity deadlines were relaxed. Current XML summaries include that passing recheck.

## Reproduce

From the repository root run application/scripts/project.ps1 build, smoke and load (PowerShell -NoProfile -ExecutionPolicy Bypass -File ...). Java 21 is required; use build -Offline only with a complete cached dependency set. Local ignored tools/data do not enter source ZIPs. Automated checks use temporary/isolated DBs and do not operate on the demo DB.

## Manual limits

Separate-laptop LAN/firewall/Wi-Fi isolation, physical interactive prompt echo, Eclipse interaction and lecturer-specific final submission still need the group's actual verification. No invented reviewer, student author, LAN result or upload is supplied. New entity references are five digits; legacy DB records are deliberately not renumbered. This copy uses fresh demo data. See [database limits](../design/DATABASE.md), [setup](../guides/SETUP.md) and [LAN checklist](../guides/LAN.md).
