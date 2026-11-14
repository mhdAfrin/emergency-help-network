# Code map

Maven root: application/. Three modules separate shared contracts, server logic and client UI. Each Java package matches its folder. Edit one responsibility rather than expanding a single main class.

| Change | File/package under application/ |
|---|---|
| Main menu wording/options | client/src/main/java/lk/emergency/client/ui/MenuRenderer.java |
| Readable cards, labels, wrapping | client/.../ui/RecordPrinter.java |
| Terminal prompts/colors/date formatting | client/.../ui/ConsoleIO.java |
| Client startup and routing | client/.../app/ClientApplication.java |
| Report input/list/detail | client/.../actions/CitizenActions.java |
| Assign/cancel/reject/close/priority | client/.../actions/OperatorActions.java |
| Accept/decline/progress/availability | client/.../actions/ResponderActions.java |
| Login and authorized restoration | client/.../actions/AuthActions.java |
| History and saved actions | client/.../actions/AccountActions.java |
| Shared authenticated requests | client/.../support/ClientContext.java |
| TCP correlation, heartbeat/reconnect | client/.../network/ |
| Pending requests/drafts and snapshot state | client/.../recovery/ |
| Server accept/read/write/routing/events | server/src/main/java/lk/emergency/server/network/ |
| State rules/authentication/idempotency | server/.../service/ |
| SQL repositories, DB executor, numeric IDs | server/.../data/ |
| SQL migrations | server/src/main/resources/db/ |
| Envelopes/DTOs/validation/framing | common/src/main/java/lk/emergency/common/ |
| Role-aware regression tests | each module's src/test/java/ |
| Actual console/load/package checks | scripts/testing/ |
| Build/run helpers and double-click launcher | scripts/project.ps1, scripts/launch.ps1, root RUN-*.cmd |

The `...` in this table abbreviates the corresponding Java package path. Entry-point classes remain tiny stable launch targets. Network services never read console input. SQL access stays on the database executor. Private-field filtering stays on the server; presentation only decides which authorized fields to print.

Before publishing changes run build, smoke and load. Do not edit an already applied V001 migration; add a numbered migration and its runner entry. Keep .local/ and target/ out of Git. They contain generated tools/cache/data/logs, not application source. Earlier historical stage notes/evidence were removed from this copy; regression tests and original requirements were retained.
