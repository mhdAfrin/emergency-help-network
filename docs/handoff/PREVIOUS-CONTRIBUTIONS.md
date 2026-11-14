# Actual contribution record and collaboration

2026-11-11: Member 1 foundation produced in this Codex-assisted session at the student's request. The student's name/Git account was not supplied. No claim is made that another member wrote, tested, reviewed or merged this code. No commits, branches, pushes or pull requests were created.

Actual work: modules/build/wrapper, framing/protocol, TCP/thread/queue foundation, SQLite migration and repositories, credentials/session authorization, seed, console client, transaction/revision/idempotency utilities, automated tests and handoff documents. Test evidence is in TEST-RESULTS.

Group workflow: use one shared repository; each member uses their real author identity and task branches (suggested codex/member1-foundation for this stage if desired). Commit meaningful actual work, open a PR, have another member review, and merge before Member 2 starts Section 17. Member 3 starts from Member 2's merged code. Do not fabricate contribution percentages by splitting existing code into fake authorship. Record real reviewer names, commands/results, dates, commit/PR links here when those events actually occur.

| Stage | Owner | Status |
|---|---|---|
| Member 1, approximate first 40% effort | Student identity pending | Foundation implemented; see checklist and actual tests |
| Member 2, next 30% | Not supplied | Not started in this session |
| Member 3, final 30% | Not supplied | Not started in this session |

## Repository organization pass

11 November 2026: at the student's request, reorganized configuration/documentation/scripts, grouped local tools/logs/database under ignored .local/, added navigation and text conventions, formatted Maven POMs, and verified all 21 automated tests plus the separate-process smoke check. The existing database was preserved byte-for-byte. No Git author identity, commit, push or reviewer approval was created.

Follow-up organization: grouped all implementation/build/runtime files under application/ so the top level contains only application/, docs/, .gitignore and README.md. Database unchanged; 21 tests and separate-process smoke passed again. No commit or push.

## Member 2 correction session — 13 November 2026

The student requested a separate member2-fixed checkout based on Member 2's existing member2-submission-snapshot branch at 8bdf413. The original snapshot implementation and commit history remain intact. This Codex-assisted correction session completed the Member 2 gaps: private post-commit notifications, reservations/presence checks, revision/cancellation/closure corrections, canonical retries, responder controls, public timelines/filtering, pending-input UX, regression tests and updated documentation.

No claim is made that another group member personally authored these corrections, reviewed them or approved them. No new Git author identity, commit, push, PR or merge was created. Member 2's actual identity/review and any later commit links should be recorded when those actions occur. Test results are recorded separately after actual execution.

## Member 3 finalization session — 13 November 2026

At the student's explicit request, created member3-finalized from pushed Member 2 fix e858384 and local branch member3-finalization. Implemented responder operations, eligible closure/history/privacy, heartbeat/reconnect, snapshot/event ordering, durable journals/drafts, startup reservation recovery, final regression/console/load measurements and delivery materials. Work was Codex-assisted. No third student's name, independent authorship, review approval or commit was invented. No new commit/push/merge/upload was performed; record the actual final author/reviewer/commit links when those events occur.

Observed Git history identifies mhdAfrin's Member 1 contribution, RIMNATH2103's Member 2 snapshot and mhdAfrin's Member 2 fix. Approximate 40/30/30 scope does not justify fabricating different authors for existing code. Final verification and remaining manual checks are recorded separately.
