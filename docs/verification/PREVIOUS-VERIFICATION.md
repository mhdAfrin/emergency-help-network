# Executed Member 1 tests

## Current layout verification — application/ grouping

The final folder layout has four top-level entries: application/, docs/, .gitignore and README.md. After moving the entire Maven application under application/, `./application/scripts/project.ps1 build` passed all **21 tests**, and `./application/scripts/project.ps1 smoke` passed with an actual server JVM and three console client JVMs. The existing database checksum is unchanged and all documentation links resolve.

Evidence: [clean build](evidence/simplified-root/build.txt), [process smoke](evidence/simplified-root/smoke.txt), [folder/database/link checks](evidence/simplified-root/structure.txt). For manual wrapper/JAR commands, first enter application/. Previous sections describe earlier layouts/runs and remain as history. The manual verification limitations still apply.

## Latest verification — repository organization

After reorganizing the files, the Java 21 clean build completed successfully on 11 November 2026 at 15:57:43 Asia/Colombo: **21 tests passed, zero failures/errors/skips**. The new `scripts/project.ps1 build` and `smoke` commands were executed successfully; the latter launched the actual server and three client JVMs using the relocated configuration and evidence folders. All documentation links were checked, and the saved database's SHA-256 was verified unchanged after its move to `.local/data/`. Four POMs were formatted without changing their parsed XML content.

Evidence for this pass: [build output](evidence/reorganization/build.txt), [process smoke output](evidence/reorganization/smoke.txt), and [structure checks](evidence/reorganization/structure-check.txt). A first attempt was blocked by this session's network restriction (`Permission denied: connect`); network permission was granted and the complete suite then passed. The blocked-run log is archived locally, not treated as a passing test. Existing manual-testing limitations below still apply.

## Original Member 1 verification record

Verified 11 November 2026, Asia/Colombo. Final Java 21 wrapper run finished at 15:42:43 +05:30 with BUILD SUCCESS in 38.128 seconds. **21 tests, 0 failures, 0 errors, 0 skipped.** A separate server plus three real console client JVM smoke check also passed. Evidence is saved under docs/verification/evidence; raw Maven log is maven-java21-verify.txt and suite counts are suite-summary.txt.

Environment: Windows NT 10.0.26200.0; x64 Intel64 Family 6 Model 186 Stepping 2; 16 logical processors reported by environment. Exact CPU marketing name/physical core count could not be queried because CIM access was denied. Eclipse was not driven. Test traffic used 127.0.0.1, not a separate-laptop LAN. Temurin OpenJDK 21.0.13+9 was downloaded using Adoptium's API and verified against the package SHA-256 before extraction. Maven wrapper 3.3.2 runs Maven 3.9.9; distribution SHA-512 was verified from Maven Central and its SHA-256 pinned in wrapper properties.

## Reproduce on this machine

From application/ in PowerShell (local tools are optional, ignored validation assets):

```powershell
$env:JAVA_HOME = "$PWD\.local\tools\jdk-21.0.13+9"
$env:PATH = "$env:JAVA_HOME\bin;C:\Windows\System32;C:\Windows;C:\Windows\System32\WindowsPowerShell\v1.0"
$env:MAVEN_USER_HOME = "$PWD\.local\tools\maven-home"
$env:TEMP = "$PWD\.local\tools\tmp"
$env:TMP = $env:TEMP
.\mvnw.cmd -B "-Dmaven.repo.local=$PWD\.local\tools\m2" clean verify
& "$env:JAVA_HOME\bin\java.exe" scripts/testing/ProcessSmoke.java
```

For an ordinary unrestricted machine use a Java 21 JDK and `.\mvnw.cmd clean verify` as in README. Workspace-specific cache/TEMP overrides avoid this session's filesystem restrictions. The generated Windows wrapper needed one command-path quoting fix because the chosen folder contains spaces. Earlier iterations found and fixed ambiguous test imports/assertions, the wrapper path issue, and Windows console UTF-8 output. Those intermediate failures are not counted as passing checks. Final evidence corresponds to corrected sources.

## Automated coverage

| Suite | Tests | Actual coverage |
|---|---:|---|
| FrameCodecTest | 6 | Fragmented and combined frames, Unicode, exact 65,536-byte payload, oversize writes, invalid lengths before allocation, interrupted prefix/payload, malformed JSON/UTF-8/null/trailing JSON, exact 100-frame queue bound |
| FoundationIntegrationTest | 7 | Temporary SQLite DB + actual loopback sockets; registration, duplicate name, privileged fields, exact password handling, bad credentials, logout, unauthenticated/wrong-role requests, own responder team only, private event recipient checks, session replacement + old socket EOF, 20 parallel socket PING jobs, malformed socket isolation, connection cap, shutdown EOF, account/team persistence and repeat seed, unsupported version/unknown request |
| DatabaseMechanismsTest | 3 | PBKDF2 salt/verification/cost, canonical key-order retry, changed-payload conflict, transaction rollback, revision helper, FKs, paired coordinates, both partial active-assignment uniqueness indexes and terminal-state release |
| RevisionTransactionTest | 2 | Two competing conditional revision updates produce exactly one success and one VERSION_CONFLICT; concurrent same-ID requests execute once; stored success replay survives DB close/reopen |
| SlowWriterTest | 2 | Deterministic blocked Socket output triggers watchdog closure (100 ms injected deadline for test); 101st queued frame closes session without blocking |
| ConnectionManagerTest | 1 | Real socket peer sends event and out-of-order responses; background delivery and correct future correlation |

SlowWriterTest uses a controlled Socket implementation to force a blocked write reproducibly; the default production deadline is 5 seconds. The generic bounded queue and malformed-peer tests plus independent per-session writers support isolation, but no claim is made that a real LAN slow-reader saturation test was performed. The concurrency test runs 20 socket jobs on a 20-thread pool; it is not a performance benchmark.

ProcessSmoke.java launches the actual shaded server JAR with --seed in an isolated temporary working directory, then starts its own server JVM and three actual client JAR JVMs. Scripted console input logs in citizen1/operator/fire1 concurrently, requests identity/PONG, lists teams for permitted roles, requests the operator-only test event, and logs out. Role/PONG/logout/team/event output is asserted. Server is terminated and awaited in finally. Logs under evidence/process-smoke and process-smoke-result.txt record the successful run. This verifies process/socket/console integration with piped input, not interactive keyboard usability.

## Password cost measurement

One untuned test sample with 600,000 PBKDF2WithHmacSHA256 iterations, 16-byte random salt, 256-bit output, Java 21: hash **328.6 ms**, verify **240.4 ms**. Measured with System.nanoTime around the actual calls during the final suite. Different-password verification and random-salt uniqueness also passed. This is one sample, not a latency distribution or a committed-change event benchmark; it is insufficient for performance guarantees. Hashing occurs outside the serialized database executor.

## Full assignment acceptance traceability

| Section 20 case | Member 1 result / next owner |
|---|---|
| 1 Registration/login/duplicates/bad credentials | Passed FoundationIntegrationTest |
| 2 Unauthenticated/wrong role | Passed FoundationIntegrationTest |
| 3 Citizen incident ownership | Member 2; no incident endpoint implemented. Own-team projection already tested. |
| 4 Incident validation/persistence/live notification | Member 2 |
| 5 Three-service assignment | Member 2 |
| 6 Concurrent operators/same team | Schema uniqueness tested; business competition remains Member 2 |
| 7 Same incident/service reservation | Partial unique index tested; business competition remains Member 2 |
| 8 Responder transitions | Member 3 |
| 9 Decline/cancel/reassignment | Database terminal-index release tested; real business flow Members 2/3 |
| 10 Closure/rejection | Members 2/3 |
| 11 Retry/conflict | Passed reusable transaction-helper tests; later services must integrate helper |
| 12 Disconnect after commit/before ACK | Saved replay mechanism persists; actual business disconnect window test Member 3 |
| 13 Fragmented/combined frames | Passed FrameCodecTest |
| 14 Length/JSON/mid-frame EOF | Passed codec tests; invalid-length/malformed socket isolation tested |
| 15 Busy responder disconnect | Member 3 once assignments exist |
| 16 Reconnect/restart | Account/team and processed-response DB restart passed; automatic reconnect/business recovery Member 3 |
| 17 Stale revisions | Atomic conditional revision helper test passed; real mutable business actions Members 2/3 |
| 18 Slow-client isolation | Queue/blocked writer/healthy malformed-peer checks passed; real socket saturation final test Member 3 |
| 19 Snapshot/event synchronization | Member 3 |
| 20 Routing/private fields | Foundation role-limited responses and private test event passed; incident/private-note routing Members 2/3 |
| 21 Console events during input | Background reader/event correlation and piped-console smoke passed; interactive typing check pending |
| 22 Graceful shutdown | Explicit server/client close, peer EOF, reader/writer cleanup tested; actual process stop awaited. Full application shutdown Member 3 |
| 23 Localhost/LAN | Localhost passed; separate-laptop permitted LAN not available/tested |

Unverified: Eclipse import/launch steps, Unix wrapper execution, physical interactive prompt behavior, separate-laptop LAN/firewall operation, full workflow, event latency median/p95, snapshot ordering, heartbeat/reconnect, incident histories, and final clean-clone submission. None is reported as passed. No real agency contacts, public deployment or non-fictional data were used.
