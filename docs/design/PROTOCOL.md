# Final wire protocol — version 1

Four-byte big-endian positive length followed by exactly that many UTF-8 JSON bytes; 1–65,536 payload bytes. Reject invalid length before allocation and close malformed JSON/UTF-8, duplicate fields, null envelopes, trailing JSON and interrupted frames safely. One writer/100-frame queue per socket.

Envelope fields: version, kind REQUEST/RESPONSE/EVENT, type, requestId (requests/responses), eventId (events), payload, response-only ok/error. Member 3 adds optional server event-only epoch and sequence metadata; requests must leave them null. Ordinary diagnostics and responses have null metadata. Unknown fields remain rejected. Every business request receives success or a structured error; malformed uncorrelatable frames close the connection.

Before authentication allow REGISTER_CITIZEN, LOGIN and PING only. LOGIN replaces the old socket, which receives SESSION_REPLACED. LOGOUT clears binding. WHO_AM_I returns own public identity. PING returns message=PONG. Passwords never enter journals/logs.

## Business requests

| Type | Fields | Role/result |
|---|---|---|
| CREATE_INCIDENT | category,address,description,optional paired latitude/longitude | CITIZEN; IncidentView, MEDIUM/REPORTED/revision 0 |
| GET_INCIDENT | id,optional assignmentOffset/timelineOffset; legacy expectedRevision ignored for reads | Owner citizen or OPERATOR; paged details, public timeline; operationalNotes only for operator |
| LIST_INCIDENTS | optional state,priority,offset,limit,history | Owner citizen or OPERATOR; default active states, explicit state filter, history=true terminal records |
| SET_PRIORITY | id,priority,expectedRevision | OPERATOR; IncidentView |
| LIST_TEAMS | optional serviceType,availability,presence | OPERATOR or responder's own team; TeamView array |
| ASSIGN_TEAM | incidentId,teamId,expectedIncidentRevision,expectedTeamRevision | OPERATOR; AssignmentView |
| CANCEL_ASSIGNMENT | assignmentId,reason,expectedRevision,expectedIncidentRevision,expectedTeamRevision | OPERATOR; AssignmentView |
| REJECT_INCIDENT | incidentId,reason,expectedRevision | OPERATOR; IncidentView |
| CLOSE_INCIDENT | incidentId,resolutionReason,expectedRevision | OPERATOR; IncidentView only when closure conditions hold |
| SET_TEAM_AVAILABILITY | availability,expectedRevision | RESPONDER; own TeamView |
| LIST_ASSIGNMENTS | optional offset,limit,history | RESPONDER; own AssignmentOffer array; default active, history=true all own states |
| GET_ASSIGNMENT | id,optional offset | RESPONDER; own assignment, related incident and paged assignment timeline; only own private notes |
| RESPOND_ASSIGNMENT | assignmentId,decision ACCEPT/DECLINE,reason,expectedRevision,expectedIncidentRevision,expectedTeamRevision | RESPONDER; decline reason required; AssignmentView |
| UPDATE_ASSIGNMENT_STATUS | assignmentId,status,note,expectedRevision,expectedIncidentRevision,expectedTeamRevision | RESPONDER; valid next EN_ROUTE/ARRIVED/COMPLETED only; AssignmentView |
| GET_SNAPSHOT | optional snapshotId,offset | Any authenticated role; SnapshotPage |
| TEST_EVENT | empty | OPERATOR diagnostic; private TEST_NOTIFICATION |

Mutations require explicit nonnegative revisions; omission is not treated as zero. IDs/actors/roles/team ownership are derived server-side. Reasons/notes are at most 500 Unicode code points after trimming; decline/cancellation/rejection/public resolution require nonblank text. Registration username is 3–40, displayName 1–80, password 8–128 Java characters preserved exactly; synthetic contact optional (≤40). Report address is 1–200, description 1–1,000 Unicode code points; paired finite coordinates are latitude ±90 and longitude ±180.

List offset defaults 0; limit defaults 5 and must be 1–5. GET_INCIDENT pages 10 assignments and 10 timeline events independently with more flags. GET_ASSIGNMENT pages 10 own timeline events. Bounded pages protect the frame limit. Server UUIDs and UTC ISO timestamps are displayed in Asia/Colombo.

## Projections and privacy

IncidentView contains public incident fields and owner ID/revision/timestamps. AssignmentView contains IDs, service, state, revision/timestamps and no private reason. PublicTimelineEvent has publicMessage but no internalNote/actor credential data. IncidentDetails contains incident, assignments, timeline, offsets and more flags; operator-only operationalNotes are added separately. AssignmentOffer includes assignment, related incident and own privateReason; an operator's private cancellation reason is not returned to a responder. Own assignment timelines expose a private note only when that responder wrote it. UserView omits contact/hash/salt/password fields.

## Live events

| Event | Payload | Authorized recipients |
|---|---|---|
| INCIDENT_CREATED | IncidentView | Operators |
| INCIDENT_UPDATED | {incident,assignment}; assignment may be null | Operators and reporting citizen |
| ASSIGNMENT_OFFERED | AssignmentOffer | Assigned responder only |
| ASSIGNMENT_UPDATED | {incident,assignment} | Operators, reporting citizen, assigned responder |
| TEAM_UPDATED | TeamView | Operators and own team for availability changes; presence changes notify operators |
| SESSION_REPLACED | message | Replaced socket, followed by closure |
| TEST_NOTIFICATION | message | Requesting operator only |

Business/presence events include envelope epoch (server-run UUID) and sequence (monotonically increasing server counter). Gaps are normal because private events go to different recipients. Events may precede the initiating response; correlate responses by request ID, not arrival order. Only post-commit events are generated, and replay emits no duplicate business events. Private notes are never included in citizen events.

## Snapshot/recovery contract

Authenticated sockets are subscribed before snapshot creation. Offset 0 with no token freezes authorized ACTIVE/REPORTED incidents, active assignments and permitted teams under the registry ordering monitor. SnapshotPage includes snapshotId,offset,nextOffset,user,incidents,assignments,teams,epoch,sequence,createdAt. All pages share one epoch/watermark. Tokens are socket-owned, expire in 30s and are released on logout/disconnect/lease cleanup. Pages contain at most five records; total active freeze limit 5,000. Histories are read separately.

Client buffers events during synchronization, atomically installs the complete snapshot, then applies only matching-epoch sequences greater than the watermark and non-older entity revisions. Equal team revisions may update presence when their sequence is newer. Buffer limit is 1,000; overflow/incomplete snapshots force a new sync instead of silently losing state. Old socket generations cannot authenticate a new connection.

Production heartbeat is 15 seconds; inbound inactivity deadline is 45 seconds and writer deadline five seconds. Reconnect delays use 1/2/4/8/up to 15 seconds with capped jitter and require login again. Passwords are not cached. Local business journals preserve original envelopes before sending, and completed draft fields persist independently. After ten seconds show confirmation pending; menu 90 reconciles/retries the unchanged original ID after login. No exactly-once network or offline event-delivery guarantee is made.

MutationSupport atomically saves canonical SHA-256 + success response with business state. Same user/request ID and identical type/payload (object key order ignored) returns the saved response; changed content returns IDEMPOTENCY_CONFLICT. Arrays/numeric representations retain type/order. Failed mutations are not cached. Authentication actions do not use saved business-response replay.

Errors: VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, TEAM_UNAVAILABLE, ACTIVE_ASSIGNMENT_EXISTS, INVALID_TRANSITION, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, UNSUPPORTED_VERSION, INTERNAL_ERROR. Unrelated incident/assignment IDs return NOT_FOUND where necessary to avoid revealing existence. VERSION_CONFLICT requires refresh and review before a new action, not silent payload mutation/retry.
