# Project commands

From the top-level project folder, run `./application/scripts/project.ps1 <action>`. From application/, use `./scripts/project.ps1 <action>`. Supported actions:

| Action | Behavior |
|---|---|
| build | Maven wrapper clean verify, including automated tests |
| seed | Create missing fictional demo users/teams without resetting saved accounts |
| server | Start the TCP server |
| client | Start one console client; use a separate terminal for each client |
| smoke | Run the Member 2 workflow with an actual server, six client JVMs and a restarted server/client |

The helper selects .local/tools/jdk-21.0.13+9 when available, then JAVA_HOME, then java on PATH. A fresh checkout requires an installed Java 21 JDK. When the original local Maven caches are present, it reuses them; otherwise Maven uses its normal cache. Nonzero tool exits propagate as errors.

Build before seed/server/client/smoke. For cross-platform or direct commands see ../../docs/guides/SETUP.md. The source-launch smoke harness currently targets Windows (java.exe), as documented in the test report.

Use `build -Offline` with an already complete cache to avoid DNS/download failures. The smoke harness uses its own DB under application/.local/testing and saves evidence under docs/verification/evidence/member2-fixed/process-smoke. It tests the Member 2 workflow, not responder progress/closure.
