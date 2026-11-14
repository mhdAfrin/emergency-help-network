# Actual process smoke logs

seed.log and server.log record an isolated seeded server. operator.log, citizen1.log, police1.log, ambulance1.log, fire1.log and fire2.log record six separate console client JVMs, not an in-process UI simulation. server-restart.log and citizen-restart.log record a second server process and fresh citizen process reading the saved report/service timeline.

The harness checks expected recipients, independent service states, private-note absence and clean logout. Inputs are piped, so this is not a claim about physical keyboard/terminal usability. No real incidents or contact data were used. The test result and limitations are in [the test report](../../../TEST-RESULTS.md) and [the process result](../process-smoke-result.txt).
