# Handoff to Member 2

Import/build/run instructions are in README. Review this foundation before merging it. No member names, Git authorship or review approvals have been fabricated.

Implemented: common protocol/types/DTOs; exact framing; bounded TCP sessions/writers; console input and background networking; response correlation; SQLite migration/full schema; serialized transactions; users/teams repositories; PBKDF2; registration/login/logout; socket identity/role checks; single-session replacement; repeatable seed; read-only teams/identity and authorized private event diagnostic; revision/idempotency helpers; automated tests and documentation.

Extension points:

1. Add incident/timeline repositories accepting Connection. Keep all related SQL inside Database.transaction or MutationSupport.execute; do not nest executor calls.
2. Add request DTOs to common and route real actions through RequestRouter. Reject unknown payload fields using Validation.payload. Derive actor from session.user, not request input.
3. Build incident service/ownership filters, report validation (display the required disclaimer before reporting), operator filtering, priority revision updates and authorized projections. No ownership service exists yet because no incidents can be created in Member 1.
4. Use MutationSupport.execute for authenticated business mutations. Check expectedRevision in SQL; persist response atomically. Send success and then role-restricted events only after commit; suppress duplicate events on replay. Callback must never write to sockets or call the registry.
5. Extend SessionRegistry with recipient routing by authenticated role/user, using short critical sections. Existing TEST_EVENT intentionally delivers only to its requesting operator. Do not broadcast operational/private payloads indiscriminately.
6. Add transactional team availability, reservation, multi-service offers, cancellation and rejection. Partial indexes are ready. Keep later responder acceptance/progress unexposed until Member 3 implements them.
7. Extend console menus only when each server action is real. Preserve reporting input/IDs when adding confirmation-pending UX; use existing ConnectionManager IDs/futures as the starting point.

Member 2 owns all Section 17 tasks, including full operator team filtering and availability controls; the current read-only team list is a foundation diagnostic. Member 3 owns ordered responder transitions, closure/histories, 15s/45s heartbeat, jittered reconnect requiring login, authorized snapshots/event buffering, BUSY reservation recovery, final privacy/race/retry/LAN/latency tests, and final delivery. Full required enums and schema do not mean those services are implemented.

Known foundation limits: plaintext trusted-LAN TCP; demo passwords; no TLS/rate-limiting/account recovery; 60-second read timeout until Member 3 heartbeat; no durable client pending journal, automatic reconnect, snapshots or offline events; login hashing occupies bounded session threads; registry protected routing serializes short foundation actions; console redraw is best-effort with ordinary terminal echo. Never use real incident/contact data.

Original instructions and every later requirement remain in docs/requirements/project-brief.txt. Complete Section 20 acceptance cases as their business features become available. TEST-RESULTS distinguishes executed foundation checks from future/unverified cases.
