# Folder layout

The project now has just four top-level entries:

```text
project/
├── application/
├── docs/
├── .gitignore
└── README.md
```

## Application

Everything needed to build and run is grouped under application/:

```text
application/
├── client/              Console client module
├── common/              Shared protocol and framing module
├── server/              TCP server, repositories and migrations
├── config/              Configuration examples
├── scripts/             PowerShell commands and Java smoke harness
├── .mvn/                Pinned Maven wrapper configuration
├── .local/              Ignored local JDK, caches, logs and saved database
├── .editorconfig        Source formatting conventions
├── .gitattributes       Application text/line-ending rules
├── mvnw / mvnw.cmd      Maven wrapper entry points
└── pom.xml              Parent three-module build
```

Maven module source folders are unchanged internally. Eclipse imports application/ as the Maven root. Use application/ as the server/client working directory for manual launches. The helper script sets that directory automatically, even when invoked from the top-level project folder.

## Documentation

docs/ contains design/, guides/, handoff/, requirements/ and verification/. Start with docs/README.md. The original supplied brief and historical raw evidence remain preserved. Old absolute paths in historical logs describe their original runs, not the current layout.

## Changes and preservation

The previously crowded root's application files were moved together into application/; no source or downloaded assets were discarded. The existing database moved with its SHA-256 verified unchanged. Its current path is application/.local/data/emergency.db. The new root .gitignore excludes that entire local-assets directory and all module target directories.

Configuration defaults are still relative to the Maven working directory: config/server.properties (with legacy server.properties fallback) and .local/data/emergency.db. The smoke harness now writes its evidence to the top-level docs/verification/evidence/ folder. Use the updated README commands; the previous root-level wrapper and scripts paths have moved.

No Git initialization, commit, push or PR was performed.

## Current correction checkout

member2-fixed/ is a separate repository beside member1-foundation/ and member2-review/. Its root still contains application/, docs/, .gitignore and README.md. Current smoke evidence is in docs/verification/evidence/member2-fixed/process-smoke. Historical relocation/checksum statements above describe the original Member 1 organization; fresh Member 2 demo databases are isolated rather than copied over existing user records.

## Finalized checkout

member3-finalized/ is a separate repository beside the preserved earlier member folders. Its root contains application/, docs/, .gitignore and README.md plus hidden Git metadata. Current tests/smoke/load evidence is in docs/verification/evidence/member3-finalized/. Local client journals, tools, test datasets, clean-export checks and source/runtime packages are ignored under application/.local/.
