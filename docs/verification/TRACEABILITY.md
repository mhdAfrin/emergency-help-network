# Acceptance-test traceability

Numbering matches Section 20 of the preserved project brief. Tests listed below were actually run in the passing 87-case suite; process checks are separately recorded in TEST-RESULTS.md. Class names resolve under application/*/src/test/java/lk/emergency/; ProcessSmoke is under application/scripts/testing/. Manual work is explicitly identified.

| # | Acceptance requirement | Executed evidence / remaining limit |
|---|---|---|
| 1 | Registration/login/duplicate/invalid credentials | FoundationIntegrationTest.registrationLoginDuplicateAndUnknownFields |
| 2 | Unauthenticated and wrong-role requests | FoundationIntegrationTest.permissionsAndPrivateEvents; FinalWorkflowTest.skippedBackwardRepeatedAndUnauthorizedProgressAreRejected |
| 3 | Citizen ownership | Member2IntegrationTest.committedReportNotifiesOnlyOperatorsAndOwnershipIsEnforced |
| 4 | Validation/persistence/live report | IncidentValidationTest; Member2IntegrationTest.committedReportNotifiesOnlyOperatorsAndOwnershipIsEnforced |
| 5 | Three services on one incident | FinalWorkflowTest.threeServicesProgressIndependentlyAndClosureRequiresAllTerminal; process smoke |
| 6 | Concurrent same-team reservation | Member2IntegrationTest.concurrentOperatorsCannotReserveSameTeam |
| 7 | Concurrent same incident/service | Member2IntegrationTest.concurrentSameIncidentServiceOffersCannotBothCommit |
| 8 | Responder transitions | FinalWorkflowTest.skippedBackwardRepeatedAndUnauthorizedProgressAreRejected; responderMutationChecksEveryRevisionAndRequiredDeclineReason |
| 9 | Decline/cancel/reassign | FinalWorkflowTest.declineIsPrivateReleasesReservationAndPermitsReplacement; Member2IntegrationTest.cancellationReleasesReservationAndNeverLeaksPrivateReason; process smoke |
| 10 | Closure/rejection | FinalWorkflowTest.threeServicesProgressIndependentlyAndClosureRequiresAllTerminal; closureWithNoCompletedAssistanceOrUnfinishedOfferIsRefused; Member2IntegrationTest rejection cases |
| 11 | Identical retries/changed payload | Member2IntegrationTest.canonicalRetryAndChangedContentAreCheckedThroughRealRouter; FinalWorkflowTest.closureAndProgressReplaysDoNotDuplicateTimeline |
| 12 | Committed mutation/unread acknowledgement | RecoveryIntegrationTest.durableSameIdRetryReconcilesCommittedUnreadResponseAfterNewClient |
| 13 | Fragmented/combined frames | FrameCodecTest; FoundationIntegrationTest.concurrentSocketClientsAndMalformedIsolation |
| 14 | Invalid length/JSON/mid-frame EOF | FrameCodecTest; FoundationIntegrationTest.concurrentSocketClientsAndMalformedIsolation |
| 15 | BUSY during disconnection | FinalWorkflowTest.presenceEventsAreOrderedAndDisconnectNeverReleasesBusy |
| 16 | Reconnect/server restart | RecoveryIntegrationTest.reconnectRequiresLoginAndSnapshotRestoresBusyAssignmentAfterServerRestart; startupReconcilesBusyFromSavedActiveAssignments; process smoke |
| 17 | Stale revisions | RevisionTransactionTest; FinalWorkflowTest.responderMutationChecksEveryRevisionAndRequiredDeclineReason |
| 18 | Slow-client isolation | SlowWriterTest; FinalWorkflowTest.realSlowReaderIsIsolatedFromHealthyReporter |
| 19 | Snapshot/event ordering | FinalWorkflowTest.frozenSnapshotHasOneWatermarkAndBuffersSubsequentChanges; LiveStateTest; RecoveryIntegrationTest.subscribeBeforeSnapshotAndBufferedUpdatesKeepLatestRevision |
| 20 | Routing/private fields | FinalWorkflowTest.snapshotsContainOnlyAuthorizedActiveState; ownAssignmentTimelineRetainsOwnNotesButHidesOperatorPrivateCancellation; Member2IntegrationTest routing/privacy cases; process smoke |
| 21 | Console events while input waits | ProcessSmoke actual six client JVMs; HeartbeatTest.backgroundHeartbeatsWorkWithoutMenusAndLostInboundClosesConnection; physical typing/repaint pending |
| 22 | Graceful shutdown | FoundationIntegrationTest.connectionCapAndCleanShutdown; repeated server/client close in integration tests; ProcessSmoke all process exits |
| 23 | Localhost/LAN | Localhost: all real-socket suites, process smoke and 20-session check passed. Separate-laptop LAN: manual pending; LAN-CHECKLIST.md |

Additional Member 3 coverage: LocalJournalTest (durability, credential exclusion, writer merging, corruption), LiveStateTest (stale revisions, epoch, buffer overflow/incomplete pages, snapshot swap), HeartbeatTest (15/45 second production constants and independent worker), RecoveryIntegrationTest (capped reconnect policy) and LocalLoadTest (20 sessions/50 measured notifications). See [actual results](TEST-RESULTS.md) and [Member 3 task checklist](../handoff/MEMBER3-CHECKLIST.md). Older test names containing 'reserved' are historical names; their refusal assertions remain valid, with successful final closure covered by FinalWorkflowTest.
