# Member 1 — all 25 tasks

This checklist follows Section 16 of docs/requirements/project-brief.txt. Completion refers to the foundation scope, not the entire application. Actual automated results and unverified manual checks are in TEST-RESULTS.md.

| # | Requirement | Implementation / evidence |
|---|---|---|
| 1 | Inspect instructions, files, course materials | Empty target inspected; parent AGENTS.md search; sibling Java socket/thread examples read; Java/Maven/Git environment checked; original lecturer brief unavailable; ARCHITECTURE documents findings |
| 2 | Maven modules and wrapper | Parent/common/server/client POMs; Maven 3.9.9 wrapper 3.3.2; pinned distribution SHA-256; tested with Java 21; Windows wrapper command quoted for paths with spaces |
| 3 | Ignore, README, configuration | .gitignore; README; config/server.properties.example; local tools and DB/build artifacts ignored |
| 4 | Envelopes, DTOs, enums, limits | common: Envelope, Dtos, Types, Validation, Json, DomainException; complete request/event/state enums plus documented foundation diagnostics |
| 5 | Framing codec and tests | FrameCodec; fragmented/consecutive/exact-limit/Unicode reads, invalid lengths, malformed JSON/UTF-8, EOF and oversize writes in FrameCodecTest |
| 6 | Configurable accept loop | TcpServer ServerSocket host/port; ServerMain properties and startup disclaimer |
| 7 | Bounded sessions | Default 32, configurable 1–200; semaphore before submission; bounded active platform-thread readers/writers; excess connection integration test |
| 8 | Registry and lifecycle | SessionRegistry; socket identity, cleanup, disconnect, replacement, graceful close; integration tests |
| 9 | Queued single writer | OutboundQueue exactly 100; nonblocking enqueue closes overflow; five-second write watchdog; SlowWriterTest exercises blocked writer and overflow |
| 10 | Basic console client | ClientMain startup/authenticated menus; host/port input; registration/login/logout/identity/role diagnostics; separate JVM smoke test |
| 11 | Background reader/writer | ConnectionManager threads independent of console input; ConsoleIO coordinates notifications and prompt output; UTF-8 output |
| 12 | Correlation and errors | UUID futures; ten-second confirmation pending retaining ID; structured server errors; out-of-order response/event client test |
| 13 | PING/PONG and authorized event | PING before/after login; operator-only TEST_EVENT → private TEST_NOTIFICATION; denied citizen event and recipient isolation tests; real process smoke |
| 14 | SQLite complete schema | V001 full six-domain-table schema plus migration tracking; FKs, checks, query indexes, both active-assignment partial unique indexes; constraint tests |
| 15 | Database executor/transactions | Database owns one executor/Connection; PRAGMAs; short commit/rollback utility; cleanup idempotence; rollback tests |
| 16 | User/team repositories | UserRepository prepared queries/inserts; TeamRepository prepared lists and repeatable seed; public projection and derived presence |
| 17 | Password hashing | PBKDF2WithHmacSHA256; 600,000 iterations; random 16-byte salt; 256-bit hash; constant-time comparison; measured sample in tests/results |
| 18 | Citizen registration | Server validation, Unicode, trimmed names/contact, exact password preservation; CITIZEN forced server-side; duplicate/privileged field tests |
| 19 | Auth/session/role checks | Generic invalid credentials; connection-bound UserView; protected request gate; forbidden role actions; responder own-team projection; login/logout tests |
| 20 | Previous session replacement | New successful login atomically replaces mapping, sends SESSION_REPLACED and closes old socket; replacement test |
| 21 | Seven seeded accounts/four teams | operator; citizen1/2; police1; ambulance1; fire1/2. Repeat --seed is non-destructive. Persistence and seed-repeat integration test |
| 22 | Startup and role menus | Only implemented actions shown. Citizen cannot see dispatch/team controls; operator list/private event; responder own team; all roles identity/PING/logout |
| 23 | Revision/dedup mechanisms | MutationSupport canonical hashing, saved successful responses in transaction, replay/conflict; requireUpdated for conditional revision SQL; concurrent update/retry/restart/rollback tests |
| 24 | Required foundation tests | Framing; actual socket concurrency; auth; permissions; saved users/teams; queue size; blocked writer; connection cap; client response correlation; transaction races. Full output and exact scope in TEST-RESULTS |
| 25 | Build/seed/run/Eclipse docs | README exact wrapper/JAR commands, configuration and Eclipse import/separate launch steps; DEMO, architecture/schema/protocol, results, contribution record, handoff/backlog |

The handoff supports multiple real console client processes, persistent accounts/teams, server-side role enforcement and reusable networking/database foundations. Members 2/3 features remain absent from menus. Eclipse GUI operation, separate-laptop LAN operation and physical console typing during events are explicitly unverified manual follow-ups, not claimed automated passes.
