# Desktop UI verification results

Verified 19 November 2026 (Asia/Colombo), Windows x64, Temurin Java 21.0.13+9. Earlier evaluation/console reports in this source copy are historical baseline, not this GUI run.

## Executed checks

| Check | Actual result |
|---|---|
| Clean Maven verify | 103 tests; common 18, server 56, client 29; zero failures/errors/skipped |
| Final desktop recheck | All 5 affected desktop adapter/view tests passed after final formatting and owned-window cleanup |
| Protocol / authorization | Desktop adapter completed independent Police/Ambulance/Fire offers and progress, closure and history; unauthorized roles/owners were denied |
| Privacy / paging | Citizen details exclude private responder notes; operator authorized notes include later timeline pages; table/list pagination beyond one page passed |
| Draft / uncertain outcome | Draft survived a new desktop session; an original journal request was reconciled once; new mutations were blocked until reconciliation |
| Actual Swing controls | Server Start/Stop seeded SQLite, bound TCP, answered EHN PING and refreshed counts. Client forms/buttons/dialogs completed report -> row/dropdown assignment -> accept -> en route -> arrived -> completed -> closure -> history |
| Live refresh / reconnect | Tables changed via live events. Server restart cleared authenticated dashboards and owned detail windows, required sign-in again and restored EN_ROUTE from authorized snapshots |
| Small-window presentation | Sorted selection kept the correct reference; cells were read-only. Submit remained inside a small form viewport. 980x710 history view rendered with visible action buttons |
| Portable runtime | Extracted Windows x64 packages ran the Swing scenario using their bundled runtime; java.desktop and java.sql were present, with no installed Java/Maven dependency for running the packages |
| Actual launchers | Source and extracted CMD/VBS launchers opened native server/sign-in windows using javaw; source native windows were inspected via Computer Use |
| Firewall helper | Syntax and WhatIf passed; desktop-specific rule targets javaw.exe, Private profile and LocalSubnet. No firewall/profile setting was changed |

The final runtime smoke uses one physical laptop with an isolated real TCP server and multiple Swing windows. Its actions use Java's own component API (not injected OS keystrokes). Screenshots below are actual Swing component renders from those windows with generated fictional records, not mockups. The first source window inspection caught a clipped form footer; the final layout fixes and visibility assertion prevent that issue.

Windows Security displayed a first-run network-permission prompt during portable-window checks. It was left unchanged. The laptop owner must review permissions according to the trusted Wi-Fi/network policy. The helper uses a distinct desktop rule and does not replace the earlier console rule.

## Evidence

- [Clean build](ui/clean-build.txt), [suite counts](ui/suite-summary.txt), [final desktop recheck](ui/desktop-recheck.txt).
- [Packaged-runtime Swing workflow](ui/desktop-smoke.txt), [package checks](ui/package-checks.txt), [artifact checks](ui/artifact-checks.txt).
- Screens: [server](ui/screenshots/server-control.png), [sign-in](ui/screenshots/connect-sign-in.png), [citizen report](ui/screenshots/citizen-report.png), [operator](ui/screenshots/operator-reports.png), [responder](ui/screenshots/responder-offer.png), [history](ui/screenshots/citizen-history.png), [details](ui/screenshots/public-details.png), [small window](ui/screenshots/minimum-window.png).

## Remaining group rehearsal

Actual two/three physical laptops, router/client isolation, each laptop's firewall/first-run permissions, third-party security tools, other operating systems/architectures, display scaling and physical keyboard/mouse usability must be rehearsed on the group's machines. No cross-laptop Wi-Fi pass or guarantee of evaluation marks is claimed. Use the [Tamil UI guide](../guides/UI-DEMO-TAMIL.md) and [physical-laptop checklist](../guides/EVALUATION-CHECKLIST.md).

The original Project Evaluation source is preserved. The UI server uses its own emergency-ui.db in the new folder. No private old database/journal is included in the portable or source ZIPs.
