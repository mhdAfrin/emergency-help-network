# Actual verification — Member 2 fixed

13 November 2026, Asia/Colombo. Checkout: member2-fixed; branch member2-submission-snapshot; baseline commit 8bdf413. Source corrections remain uncommitted. This report is based on actual execution, not on the presence of test files.

## Result

**Java 21 clean Maven verification passed: 60 tests, zero failures, zero errors, zero skipped.** The final recorded run finished at 15:32:16 +05:30 in 2 minutes 54 seconds. The original snapshot baseline passed 37 tests; 23 regression cases were added (20 server integration cases and three client pending-action cases).

**The packaged process smoke passed** with one server JVM and six actual client JVMs. It drove console input through report creation, live operator event, priority, three service offers, independent citizen service views/public timeline, cancellation, a replacement fire team, a separate public rejection, private recipient/note checks, logout, then a restarted server and new citizen JVM recovering the saved report/service timeline. All processes were stopped. This is scripted console verification; physical keyboard usability remains a manual check.

| Suite | Tests |
|---|---:|
| FrameCodecTest | 6 |
| ValidationTest | 12 |
| DatabaseMechanismsTest | 3 |
| FoundationIntegrationTest | 7 |
| IncidentValidationTest | 4 |
| Member2IntegrationTest | 20 |
| RevisionTransactionTest | 2 |
| SlowWriterTest | 2 |
| ConnectionManagerTest | 1 |
| PendingActionsTest | 3 |
| Total | 60 |

## Evidence and reproduction

- [Snapshot baseline build](evidence/member2-fixed/snapshot-baseline-build.txt)
- [Final clean build](evidence/member2-fixed/clean-build.txt)
- [Suite summary](evidence/member2-fixed/suite-summary.txt)
- [Actual process smoke result](evidence/member2-fixed/process-smoke-result.txt)
- [Console/server logs](evidence/member2-fixed/process-smoke/README.md)
- [Earlier verification history](PREVIOUS-VERIFICATION.md)

Executed from the checkout root in PowerShell with the local Java 21 toolchain and complete local cache:

```powershell
$env:MAVEN_ARGS = '--offline'
.\application\scripts\project.ps1 build
.\application\scripts\project.ps1 smoke
```

The helper also supports the equivalent `build -Offline` option. Leave offline mode unset on a fresh machine until Maven/dependencies have been downloaded. Tools are pinned by the existing wrapper/POMs (Maven 3.9.9, Java release 21); this machine used Temurin 21.0.13+9. Test traffic used localhost. Temporary DBs isolate test changes from user records.

## What the regression tests actually prove

- Committed report/timeline/processed response exist before operators receive INCIDENT_CREATED; unrelated citizens/responders receive no report event.
- Citizen ownership, operator state/priority filters, public details/paging, Unicode and finite coordinate validation, unknown privileged fields, explicit missing revisions, and stale updates are enforced through the router.
- Same ID/content replays; reordering JSON object keys still replays; changed content conflicts; replay does not duplicate records/reservations/recipient events. A golden canonical encoding checks cross-JVM-stable hashing.
- Online/available teams become BUSY; offline/unavailable/busy/stale teams are refused; unassigned responders alone change availability; a different team cannot be selected through spoofed payload fields.
- Three services coexist on one incident; responders inspect only their own active offers; unrelated responders receive no offer.
- Two concurrent operators competing for the same team or same incident/service cannot both commit. Database partial unique indexes remain covered by the original schema tests.
- Cancellation checks assignment/incident/team revisions, allows OFFERED/ACCEPTED, refuses EN_ROUTE/ARRIVED/terminal fixtures, releases availability and permits replacement. Private reasons are saved but absent from citizen details/events and responder updates.
- Never-assigned rejection is public and terminal; historical assignments block rejection. Premature closure remains reserved and returns INVALID_TRANSITION.
- Injected timeline failures roll back report/offer/incident/team changes and the processed response. No success notification is sent for failed work.
- BUSY reservations and saved report/assignment records persist after disconnect and DB/server restart. This does not claim complete Member 3 snapshot/reconnect behavior.
- A client intentionally drops an unread acknowledgement after observing the server's committed processed-request row; reauthentication and the same ID replay the saved result. The ACK may already have reached a TCP buffer; no packet-loss window or exactly-once delivery is claimed.
- PendingActions tests use controlled delayed responses to verify retained input, late-response reconciliation, identical-ID retry, cross-user isolation and unresolved outcomes on disconnect. Production waits ten seconds; tests inject short waits rather than claiming a measured ten-second latency.

Accepted/advanced assignment states are direct test fixtures because actual responder transitions belong to Member 3. The process smoke never invokes those future endpoints.

## Limits and remaining manual checks

Separate-laptop LAN/firewall/device isolation, Eclipse interaction and physical typing while events arrive are not tested here. No median/p95 committed-change latency benchmark was performed. The original one-sample PBKDF2 measurements remain historical evidence, not a performance guarantee.

Automatic heartbeat/reconnect, snapshots/event buffering, durable pending drafts, responder acceptance/progress, completion/closure and complete histories remain Member 3 work. SlowWriterTest is a deterministic blocked-output test, not a real LAN slow-reader saturation benchmark. There is no persistent offline event journal, TLS, real dispatch or production deployment.

The fixed checkout uses fresh isolated/demo DBs. Old snapshot business databases with noncanonical fingerprints/unreserved offers were preserved in their original folder and are not silently migrated. Protocol DTOs/revisions changed; use the matching rebuilt client/server pair.
