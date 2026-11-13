# Evidence index

[Current verification report](../TEST-RESULTS.md) describes the final Member 3 checkout. [Current suite summary](member3-finalized/suite-summary.txt) was parsed from the actual passing Maven XML reports.

member3-finalized/ contains the final clean build, separate-process smoke result/logs, clean-source-export build/seed/smoke results, measured latency CSV/method and one clearly labelled failed intermediate build. Paths and timestamps in logs describe the checkout used for each actual run; the clean-export smoke's process logs are inside the ignored clean-export directory, while its result is preserved here. Current root process-smoke/ logs are the final checkout's actual six-client run.

member2-fixed/ and [Member 2 report](../MEMBER2-VERIFICATION.md) retain the previous correction stage. Earlier top-level records, reorganization/ and simplified-root/ are historical Member 1 evidence; their totals and paths apply to those runs only. Do not interpret a historical report's backlog as a current missing implementation.
