# Member 3 — all 29 tasks and actual status

Scope: Section 18 of the original project brief. Implementation and machine-supported checks are completed locally; hardware/submission/after-commit checks are explicitly distinguished below. Actual results are in ../verification/TEST-RESULTS.md.

| # | Task | Evidence/status |
|---|---|---|
| 1 | Verify Member 2 build/tests/workflow | e858384 baseline retained; prior 60 cases included in final suite; final console workflow exercises Member 2 behavior |
| 2 | Responder accept/decline/reasons | AssignmentService.respond; decline requires private reason |
| 3 | Ordered progress | ACCEPTED -> EN_ROUTE -> ARRIVED -> COMPLETED only |
| 4 | Reject skip/backward/stale/unauthorized | FinalWorkflowTest and role/ownership fixtures |
| 5 | Release terminal reservations | Atomic AVAILABLE release on DECLINED/COMPLETED, existing CANCELLED release retained |
| 6 | Reassignment after decline/cancel | Partial-index/check contract; console replacement fire offer |
| 7 | Keep incidents needing coordination visible | ACTIVE persists after all declines/cancellations; closure guard refuses no completed assistance |
| 8 | Authorized progress notifications | Post-commit public IncidentUpdate/AssignmentUpdate; no private note fields |
| 9 | Separate service display | Console rows per assignment/service; smoke shows one ARRIVED while others ACCEPTED |
| 10 | Eligible closure | ACTIVE + at least one COMPLETED + every assignment terminal, expected revision/public resolution |
| 11 | Complete histories/timelines | Citizen/operator terminal history and details; responder own history/GET_ASSIGNMENT timeline; operator operational notes |
| 12 | Citizen/private-note separation | Role DTOs and own-note checks; final privacy regression and console checks |
| 13 | Heartbeat/inactivity | Production 15s/45s; timing injection tests and partial-frame idle tests |
| 14 | Background reconnect/state | Capped jittered 1/2/4/8/15s supervisor, no password replay, login required |
| 15 | Preserve BUSY on disconnect | Stored reservation retained; restart reconciliation derives active reservations |
| 16 | Operator presence updates | Sequenced TEAM_UPDATED on responder login/logout/disconnect |
| 17 | Authorized snapshots | Socket-owned frozen active projections, bounded pages/token lease |
| 18 | No-gap snapshot/event/revision handling | Epoch/watermark + buffer + atomic swap + older-revision rejection; overflow forces fresh sync |
| 19 | Restore active assignments | New login snapshot after server reconnect/restored BUSY, verified in real process smoke |
| 20 | Pending reconciliation/unread ACK | Durable journal before send, same-ID explicit retry, real committed/unread-response regression |
| 21 | Graceful shutdown/slow-client isolation | Cooperative joins/watchdogs; deterministic writer and actual slow-reader tests |
| 22 | State/privacy/retry/restart/ordering tests | Final suite and explicit acceptance traceability |
| 23 | Real multi-client workflow | Actual server + six client JVMs through complete closure/reconnect/history/draft flow |
| 24 | Separate-laptop LAN | Manual pending: no authorized second laptop endpoint available; LAN-CHECKLIST.md supplied |
| 25 | 20-session headless check | Executed with 20 authenticated real TCP sessions and 500 PING exchanges |
| 26 | Commit-to-receipt latency/console observation | Executed 50 report events; method/median/p95/machine/network/CSV supplied; console behavior observed separately, not repaint timing |
| 27 | Diagrams/traceability/troubleshooting/demo | Current design, traceability and guides supplied |
| 28 | Clean build/seed/run/checks | Fresh source-export verification supplied; exact final Git-commit clone awaits the student's actual commit/push |
| 29 | Final handoff/submission materials | Source/runtime packages + guides/checklists prepared; lecturer-specific upload/format confirmation and actual submission remain group actions |

No unexecuted LAN/Eclipse/physical keyboard/upload/after-commit action is reported as passed. Known prototype limits and historical failed runs remain documented.
