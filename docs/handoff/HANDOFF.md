# Handoff to Member 3

Continue from Member 2's reviewed and merged branch. Read the full brief in ../requirements/project-brief.txt, the current protocol, the Member 2 checklist, and actual test results. The original Member 1 handoff is preserved in MEMBER1-HANDOFF.md.

## Implemented foundation for your stage

Persistent reports/public timelines; citizen ownership; operator filters/priority; online/available team selection; transactional three-service offers and BUSY reservation; own-offer inspection; availability controls; eligible cancellation/reassignment and public rejection; post-commit private events; canonical saved-response replay; explicit revision checks; ten-second pending outcomes with retained same-session input/ID. Full schema and partial active-assignment indexes remain unchanged.

Use the existing AssignmentRepository/Service and MutationSupport. Accept a server-authenticated user and derive its team from TeamRepository.forUser. Mutations must run on the supplied Connection, not recursively enter the database executor. Commit state + timelines + team/incident revisions + success response atomically, then publish through NotificationService only when replayed=false. Public DTOs contain no private notes.

## All Member 3 work still required

1. Implement responder accept/decline and ordered ACCEPTED -> EN_ROUTE -> ARRIVED -> COMPLETED transitions. Reject skipped/backward/stale/unauthorized changes. Decline requires a private reason.
2. Release reservations on terminal responder outcomes and support reassignment after decline. Preserve ACTIVE incidents needing another offer.
3. Route progress events privately to the citizen/operators/relevant responder; retain separate service states and revision handling.
4. Implement closure only for ACTIVE incidents with at least one COMPLETED assignment and ALL assignments terminal. CLOSE_INCIDENT is currently reserved. Do not restore the snapshot's premature closure behavior.
5. Complete histories and appropriate public/operational projections, keeping private cancellation/decline notes out of citizen responses.
6. Add heartbeat every 15 seconds, 45-second inbound inactivity detection, and reconnect backoff 1/2/4/8/up to 15 seconds with jitter. Require login again. Current readers block; no automatic reconnect is claimed.
7. Implement authorized snapshots, subscribe-before-snapshot ordering, event buffering and revision application without gaps. GET_SNAPSHOT is currently reserved.
8. Add durable draft/pending-action recovery across disconnection/process exit. PendingActions currently keeps up to 20 in-memory requests isolated by user; it retains the original envelope/future after timeout and supports menu 90 same-ID retries. Do not blindly issue a new ID after an uncertain commit.
9. Reconcile BUSY state from active assignments on startup/reconnect, preserving disconnected reservations. Stage 2 already persists BUSY atomically; tests verify it is not freed on disconnect/restart, but complete responder synchronization remains your task.
10. Finish shutdown/slow-reader isolation, complete workflow testing, permitted separate-laptop LAN verification, clean-clone delivery and measured event latency (method, samples, median, p95, machine/network). No such final measurements are claimed here.

The Member 2 process smoke uses six real client JVMs and an isolated DB. Extend it for acceptance/progress/closure once those actions exist. Full acceptance remains governed by Sections 18 and 20 of the original brief, not only the passing tests currently present.

Known limits: plaintext trusted-LAN TCP; demo accounts; no real dispatch; best-effort events without offline journal; same-session pending input only; no automatic presence notifications or snapshots; public detail/list pages are bounded; ordinary terminal echo can split partially typed input despite prompt redraw. Private notes are stored but not exposed by the Member 2 public detail endpoint. Eclipse interaction and separate-laptop LAN still need manual checks.
