# Protocol v1 — Member 2

Four-byte big-endian positive length followed by exactly that many UTF-8 JSON bytes; maximum 65,536 payload bytes. Invalid size is rejected before allocation. Malformed JSON/UTF-8, duplicate fields, null envelopes, trailing JSON and mid-frame EOF close only that connection. Each socket has one writer and a 100-frame outgoing queue.

Envelope fields: version=1, kind REQUEST/RESPONSE/EVENT, type, requestId (UUID for requests/responses), eventId (UUID for events), payload, response-only ok/error. Requests cannot set eventId/ok/error. Unknown envelope/payload fields are rejected. Before authentication only PING, LOGIN and REGISTER_CITIZEN are accepted.

```json
{"version":1,"kind":"REQUEST","type":"PING","requestId":"11111111-1111-1111-1111-111111111111","eventId":null,"payload":{},"ok":null,"error":null}
```

All payloads are objects. Optional filter/page fields can be omitted; mutation revision fields MUST be present and nonnegative (missing is not treated as zero). IDs/actors/roles/team ownership come from server state and authenticated identity. External caller-supplied SQL fields are never interpolated.

## Implemented requests

| Type | Payload fields | Authorization / response |
|---|---|---|
| REGISTER_CITIZEN | username, displayName, password, optional contactNumber | Public; always creates CITIZEN; UserView; does not authenticate |
| LOGIN | username, password | Public; binds/replaces socket identity; UserView |
| LOGOUT | none | Authenticated; clears identity |
| PING | none | Public; message=PONG |
| WHO_AM_I | none | Authenticated; own UserView |
| CREATE_INCIDENT | category, address, description, optional latitude/longitude together | Citizen; IncidentView; MEDIUM, REPORTED, revision 0 |
| GET_INCIDENT | id, optional assignmentOffset/timelineOffset; optional expectedRevision accepted for legacy read clients but ignored | Citizen owner or operator; IncidentDetails |
| LIST_INCIDENTS | optional state, priority, offset, limit | Citizen-owned or operator-wide; array of IncidentView |
| SET_PRIORITY | id, priority, expectedRevision | Operator; IncidentView |
| LIST_TEAMS | optional serviceType, availability, presence | Operator sees matching teams; responder only own matching team; array of TeamView |
| ASSIGN_TEAM | incidentId, teamId, expectedIncidentRevision, expectedTeamRevision | Operator; AssignmentView |
| CANCEL_ASSIGNMENT | assignmentId, reason, expectedRevision, expectedIncidentRevision, expectedTeamRevision | Operator; AssignmentView |
| REJECT_INCIDENT | incidentId, reason, expectedRevision | Operator; IncidentView |
| SET_TEAM_AVAILABILITY | availability, expectedRevision | Responder's own team only; TeamView |
| LIST_ASSIGNMENTS | optional offset, limit | Member 2 extension; responder's own active offers/current assignments; array of AssignmentOffer |
| TEST_EVENT | none | Operator-only diagnostic; private TEST_NOTIFICATION |

Read offsets default to 0 and must be nonnegative. List limits default to 5 and must be 1–5. GET_INCIDENT returns up to 10 assignments and 10 public timeline rows, with independent offsets and moreAssignments/moreTimeline flags. Fetch the next page when flagged; results are never silently presented as a complete unbounded history. Timelines/assignment pages are ordered by createdAt,id. Incident lists are newest first. Full history controls remain Member 3 work.

## Response DTOs

- UserView: id, username, displayName, role. No password/contact fields.
- TeamView: id, name, serviceType, availability, revision, presence.
- IncidentView: id, citizenId, category, address, description, latitude, longitude, priority, state, publicResolutionReason, createdAt, updatedAt, closedAt, revision.
- AssignmentView: id, incidentId, teamId, serviceType, state, createdAt, updatedAt, revision. No private reason.
- PublicTimelineEvent: id, incidentId, assignmentId, eventType, publicMessage, createdAt. No internal note or actor identity.
- IncidentDetails: incident, assignments, timeline, assignmentOffset, timelineOffset, moreAssignments, moreTimeline.
- AssignmentOffer: assignment, incident. Contains only the authenticated responder's own assignment and its related incident.

Registration: username 3–40 Unicode code points, displayName 1–80; password 8–128 Java characters preserved exactly; synthetic contact optional (maximum 40). Report address 1–200 code points, description 1–1,000; valid finite paired coordinates. Reasons 1–500 nonblank code points after trimming. Enum values are case-sensitive on the wire. Server UUIDs and UTC ISO timestamps are displayed by the console in Asia/Colombo.

## Events and recipients

| Event | Payload | Recipients |
|---|---|---|
| INCIDENT_CREATED | IncidentView | Connected authenticated operators |
| INCIDENT_UPDATED | IncidentUpdate {incident, assignment}; assignment null for priority/rejection | Operators and reporting citizen |
| ASSIGNMENT_OFFERED | AssignmentOffer | Assigned responder only |
| ASSIGNMENT_UPDATED | {assignment, incidentId} | Operators, reporting citizen, assigned responder |
| TEAM_UPDATED | TeamView | Operators and that team's responder |
| SESSION_REPLACED | message | Replaced socket, followed by closure |
| TEST_NOTIFICATION | message | Requesting operator only |

Events are generated after commit and suppressed on replay. Arrival may precede the initiating response, so clients must correlate response IDs independently from event handling. Queue overflow/write timeout isolates the failing client. Presence-change notifications, buffering, snapshot ordering and durable offline delivery are not implemented; persistent offline delivery is not required.

## State, errors and retries

Offers require an ONLINE/AVAILABLE team and atomically reserve BUSY. One active assignment per team and per incident/service is enforced by checks and partial unique indexes. First offer changes REPORTED to ACTIVE. Only OFFERED/ACCEPTED can be cancelled; cancellation returns AVAILABLE and keeps the incident ACTIVE. Reject only REPORTED incidents with no assignment history. CLOSED/REJECTED are terminal.

Error codes remain VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, TEAM_UNAVAILABLE, ACTIVE_ASSIGNMENT_EXISTS, INVALID_TRANSITION, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, UNSUPPORTED_VERSION, INTERNAL_ERROR. Unauthorized citizen IDs return NOT_FOUND without revealing another citizen's report. VERSION_CONFLICT requires refresh and review; do not automatically repeat a changed action.

All authenticated business mutations use MutationSupport: SHA-256 of recursively sorted type/payload JSON objects; arrays retain order and numeric representations retain type. The user/request-ID key and original success response commit atomically with the change. Same content/order-independent keys replay the original response. Different content conflicts. Responses to failed mutations are not cached. Registration/login/logout do not use this mechanism.

After ten seconds the client reports confirmation pending and retains the original request/input. Menu 90 can inspect it, consume a late response or retry unchanged with the SAME ID. The bounded pending store is isolated by logged-in user and kept in memory; durable drafts and reconnect reconciliation are Member 3 work. No exactly-once network delivery is claimed.

CLOSE_INCIDENT, RESPOND_ASSIGNMENT, UPDATE_ASSIGNMENT_STATUS and GET_SNAPSHOT remain reserved and return structured INVALID_TRANSITION after authentication/role checks. No premature closure endpoint or working menu is exposed.
