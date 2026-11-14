# Actual verification — Member 3 finalized

13 November 2026, Asia/Colombo. Checkout: member3-finalized; local branch member3-finalization; baseline e858384. Final Member 3 changes remain uncommitted. This report describes actual execution, with manual obligations recorded separately.

## Verified results

**Clean Java 21 Maven verification passed: 87 tests, zero failures/errors/skips.** The final checkout run ended at 19:12:30 +05:30 in 2 minutes 18 seconds. All 60 prior Member 1/2 cases remain included, with 27 Member 3 cases added. See [build log](evidence/member3-finalized/clean-build.txt) and [parsed suite summary](evidence/member3-finalized/suite-summary.txt).

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
| server | SlowWriterTest | 2 |
| client | ConnectionManagerTest | 1 |
| client | HeartbeatTest | 2 |
| client | LiveStateTest | 4 |
| client | LocalJournalTest | 4 |
| client | PendingActionsTest | 3 |
| client | RecoveryIntegrationTest | 4 |
| Total | | 87 |

**Separate-process console smoke passed:** one server JVM and six console client JVMs exercised report, live operator notification, three services, decline/replacement, accept, independent EN_ROUTE/ARRIVED/COMPLETED, eligible closure and histories. A restarted server caused background client reconnect while console input was idle; explicit re-login and authorized snapshots restored BUSY/EN_ROUTE. A fresh citizen process recovered closed history; an incomplete report draft survived EOF/restart and resumed. Recipient/private-note checks and process exits passed. See [result](evidence/member3-finalized/process-smoke-result.txt) and [actual console logs](evidence/member3-finalized/process-smoke/README.md).

**Clean source-export verification passed:** the source ZIP was extracted into a fresh directory without target output or bundled local tools/data. Its own Maven wrapper/source built with the external Java 21 toolchain and an existing offline dependency cache; all 87 cases passed (finished 19:21:42 +05:30, 2 minutes 53 seconds). That export seeded successfully and passed the same complete multi-process smoke. See [build](evidence/member3-finalized/clean-export-build.txt), [seed](evidence/member3-finalized/clean-export-seed.txt), [smoke](evidence/member3-finalized/clean-export-smoke.txt). Later changes were delivery documentation/evidence and helper formatting, with source consistency checked during final packaging. This is a fresh source-export check; an exact clone of the final Git commit awaits that actual commit.

## Actual load and latency measurement

20 authenticated real TCP sessions completed 500 PING exchanges without failed requests. While those sessions stayed connected, 50 sequential CREATE_INCIDENT notifications were measured. Start: System.nanoTime immediately after JDBC commit returned in the database executor. End: System.nanoTime in the operator background callback after decoding INCIDENT_CREATED. One JVM supplies a shared monotonic clock; messages still travel through actual loopback sockets. The interval includes post-commit routing, queueing, TCP and decoding, and excludes the database write itself.

**Median: 0.939600 ms; p95 (nearest rank): 1.311500 ms; n=50.** [Raw CSV](evidence/member3-finalized/load-latency.csv), [method/environment/results](evidence/member3-finalized/load-latency.txt). Windows 11 10.0 amd64, OpenJDK 21.0.13+9-LTS, Intel64 Family 6 Model 186 Stepping 2 GenuineIntel, 16 available processors; JVM max heap 4,219,469,824 bytes (not physical RAM). Network: 127.0.0.1, one machine. These values do not measure separate-laptop LAN performance or physical terminal repaint. The console smoke separately verified event handling while menus waited for input.

## What the new tests establish

- Real responder transitions enforce ownership, all expected revisions, required decline reasons and strict next-state ordering. Terminal release/reassignment and multi-service independence persist atomically with timeline/dedup records.
- Closure requires ACTIVE, at least one COMPLETED assignment and every assignment terminal; retries do not duplicate timeline changes. Declined/cancelled incidents stay visible while coordination remains necessary.
- Citizen/event projections hide operational notes; responders see only their own assignment/timeline notes. Operators retain private operational detail access.
- Heartbeats run without menu input; idle and stalled partial-frame sockets expire while a healthy peer survives. A real deliberately slow socket reader is isolated, in addition to deterministic blocked-writer/queue tests.
- Socket-owned frozen snapshots use one epoch/watermark, authorization and bounded paging. Client buffers/swaps correctly, rejects stale revisions/old epochs and refuses incomplete/overflowed synchronization.
- Disconnect/restart preserves BUSY reservations; new connections require login and recover active assignments. Backoff constants and capped jitter are tested.
- Durable pending requests/drafts survive new journal instances, merge concurrent writers, exclude credentials and remain user-scoped. A real committed mutation with an unread acknowledgement is retried from a new client using the original ID and payload, returning the saved result once. The ACK may already have reached a TCP buffer; this does not claim exactly-once network delivery.

## Reproduction

From the checkout root in PowerShell:

```powershell
.\application\scripts\project.ps1 build
.\application\scripts\project.ps1 smoke
.\application\scripts\project.ps1 load
.\application\scripts\project.ps1 package
```

Use build -Offline only after the wrapper/dependencies are cached. Java 21 is required; the pinned Maven wrapper uses 3.9.9. The ignored local toolchain/cache on this machine is optional and excluded from source ZIPs. Automated tests use temporary databases and loopback sockets. Smoke/load use isolated local databases, not the demo database.

## Failed attempt and verification limits

An intermediate full run had one login socket timeout after an unusually long elapsed run (~2 hours 10 minutes). It is preserved as [failed attempt](evidence/member3-finalized/intermediate-failed-build.txt), not counted as a pass. The isolated failing case passed on rerun; the subsequent full 87-case run and independent clean-export 87-case run both passed. The underlying host scheduling delay was not established as a proven cause.

Not executed: separate-laptop LAN/firewall/device-isolation, physical keyboard/terminal repaint checks, interactive Eclipse use, a final-commit Git clone, lecturer-specific formatting checks or actual LMS upload. Follow [LAN checklist](LAN-CHECKLIST.md), [all 29 tasks](../handoff/MEMBER3-CHECKLIST.md) and [submission checklist](../submission/CHECKLIST.md). No unavailable machine, reviewer, student identity, upload or approval is fabricated.

Prototype limits: trusted localhost/LAN only, unencrypted TCP, no real dispatch; bounded active snapshots (5,000 combined records, 5 records/page, 30-second lease), client event buffer 1,000 and pending journal 20 per user. Overflow requires fresh synchronization; no durable offline event stream. Historical terminal data uses explicit paged queries. Pair matching rebuilt clients/server. Prior snapshot databases with old fingerprints are not silently migrated. Existing Member 1/2 evidence remains historical: [Member 2 report](MEMBER2-VERIFICATION.md), [earlier verification](PREVIOUS-VERIFICATION.md).
