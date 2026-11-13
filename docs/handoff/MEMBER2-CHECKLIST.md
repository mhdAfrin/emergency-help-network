# Member 2 — all 26 tasks

Scope: Section 17 of the preserved project brief. The snapshot at 8bdf413 is retained in Git history; this working copy corrects and completes its Member 2 behavior. Test outcomes are recorded separately in ../verification/TEST-RESULTS.md.

| # | Required task | Implementation and verification location |
|---|---|---|
| 1 | Verify Member 1 build/tests | Baseline 37-test snapshot build preserved as evidence; original tests retained |
| 2 | Incident/timeline repositories/services | IncidentRepository, IncidentTimelineRepository, IncidentService |
| 3 | Report validation | Unicode/length/enum/paired finite coordinate checks; IncidentValidationTest and socket validation tests |
| 4 | Server-generated records/defaults | UUID/UTC, session owner, MEDIUM/REPORTED/revision 0 |
| 5 | Atomic report/public timeline | One transaction with saved response; forced timeline-failure rollback test |
| 6 | Shared business deduplication | Canonical MutationSupport for every business mutation; replay/conflict/golden encoding tests |
| 7 | Confirmed/pending submission | Confirmed reference; PendingActions retains input/ID after 10s, menu 90; timeout/retry/disconnect tests |
| 8 | Citizen-owned lists/details | Server owner predicates and NOT_FOUND on unrelated detail; ownership tests |
| 9 | Operator lists/filter/details | IncidentFilter state/priority and bounded pages; integration tests |
| 10 | Live report notifications | NotificationService after commit, operators only; recipient and persisted-state tests |
| 11 | Priority and revisions | Conditional revision SQL, public event, terminal guards; stale socket and service tests |
| 12 | Citizen/operator menus | Real report/list/detail/timeline/priority actions; startup and pre-report disclaimer; process smoke |
| 13 | Team filters/presence | TeamFilter service/availability/presence; responder own team only |
| 14 | Responder availability | Session team derived server-side; AVAILABLE/UNAVAILABLE only unassigned; stale/busy/spoof tests |
| 15 | Atomic offers/reservations | Online/available checks, OFFERED + BUSY + incident ACTIVE + timeline + saved response |
| 16 | One active assignment/team | Checks and partial unique index; two-operator competing-team test |
| 17 | One active assignment/incident/service | Checks and partial unique index; competing-service test |
| 18 | Multi-service incident | Independent police/ambulance/fire records; real socket and six-client smoke |
| 19 | First offer activates report | Incident revision/state change committed with offer; rollback and multi-service tests |
| 20 | Private offer routing | Assigned responder only; public incident/service updates for citizen/operators; replay suppresses events |
| 21 | Offer controls/inspection | Operator offer menu and responder own current-offer menu; no accept/progress placeholder |
| 22 | Eligible cancellation | OFFERED/ACCEPTED only; all affected revisions checked; private reason; AVAILABLE release; rollback/reassignment/privacy fixtures |
| 23 | Never-assigned rejection | REPORTED + no assignment history; public reason; terminal guard and historical fixture tests |
| 24 | Public/private projections | PublicTimelineEvent/AssignmentView exclude notes; public event payloads and details tested |
| 25 | Required tests | Existing tests plus Member2IntegrationTest, PendingActionsTest and actual console process workflow |
| 26 | Documentation/handoff | Updated README, protocol/design/demo/results/traceability and Member 3 handoff; honest contribution record |

Tests do not replace code review. Snapshot/reconnect/history/acceptance/progress/closure and final delivery requirements remain Member 3. Separate-laptop LAN and physical keyboard/prompt checks remain manual; no final performance claims are made.
