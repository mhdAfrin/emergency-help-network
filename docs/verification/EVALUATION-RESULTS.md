# Wi-Fi evaluation edition verification

Verified 19 November 2026 (Asia/Colombo), Windows 11 x64, Temurin Java 21.0.13+9.

## Executed checks

| Check | Actual result |
|---|---|
| Clean Maven verify | 98 tests: common 18, server 56, client 24; 0 failures, errors or skipped |
| Server configuration | CLI values override files; seed/run use the same explicit DB; bad flags, bounds and missing explicit config are rejected |
| Protocol probe | Real framed PING/PONG required; a TCP-only/wrong reply is rejected |
| Six console clients through loopback | Full workflow, multi-service assignment, decline/replacement, independent progress, closure/history, private-note filtering, restart/re-login and draft recovery passed |
| Six console clients through the active Wi-Fi IPv4 | Same full workflow passed with bind 0.0.0.0 and destination 172.20.10.5; separate client JVMs on ONE physical laptop |
| Load regression | 20 authenticated TCP sessions, 500 PING exchanges, no failed requests; 50 event-latency samples; median 0.987350 ms, p95 2.969100 ms |
| Extracted Windows x64 server ZIP | Actual CMD/PowerShell launcher uses bundled Java, seeds SQLite and starts wildcard listener |
| Extracted Windows x64 client ZIP | Actual launcher/probe works without installed Java/Maven; login, five-digit report reference, saved endpoint, logout and exit passed |
| Portable server restart | Same extracted folder preserves report; repeated seed does not reset data |
| Network-info / firewall helper | Adapter listing passed; firewall WhatIf preview passed; actual rule installation/removal was not executed |

## Evidence and interpretation

- [Build](evaluation/build.txt), [suite counts](evaluation/suite-summary.txt).
- [Loopback workflow](evaluation/smoke-loopback.txt) and [Wi-Fi-interface workflow](evaluation/smoke-wifi-interface.txt), with corresponding console-loopback/ and console-wifi-interface/ process logs.
- [Load report](evaluation/load.txt), [latency definition](evaluation/load-latency.txt) and [raw samples](evaluation/load-latency.csv).
- [Portable package checks](evaluation/package-checks.txt) and portable/ launcher logs.
- [Artifact and original-copy checks](evaluation/artifact-checks.txt).

The load check uses real loopback TCP with server/headless clients sharing one JVM and monotonic clock. It measures from immediately after DB commit returns to the operator callback after decode; it excludes the DB write, physical terminal repaint and separate-laptop Wi-Fi latency. It is not a LAN benchmark.

The Wi-Fi-interface workflow uses this machine's actual Wi-Fi address but remains on ONE physical laptop. It verifies endpoint/binding/CLI and process-level behavior; it cannot establish router forwarding, access-point isolation, another laptop's firewall or another OS.

An initial sandboxed JVM crashed in Windows native socket code (EXCEPTION_INVALID_HANDLE in ntdll/mswsock) during FoundationIntegrationTest. That incomplete run is not counted as a pass. The clean rerun outside the sandbox passed all 98 tests. Diagnostic details are retained locally under application/.local/verification/ and excluded from source exports.

## Group rehearsal still required

Use [EVALUATION-CHECKLIST.md](../guides/EVALUATION-CHECKLIST.md) with your actual two/three laptops. Record IPs, matching port, OS/architecture, users, time, connectivity, reports/notifications, offers, progress, closure, histories and reconnect/re-login. Actual firewall rule behavior, third-party security prompts, physical typing/display and the lecturer's Wi-Fi are manual checks. No remote-laptop pass is claimed.

Earlier RESULTS.md and current/ evidence is retained as historical baseline from the simplified final project. This document and evaluation/ describe this upgrade. Original project source and private records were preserved; only the requested Project Evaluation copy was changed.
