# Protocol v1

TCP is a byte stream: every message is a four-byte signed big-endian positive length followed by exactly that many UTF-8 JSON bytes (1–65,536, excluding prefix). ReadNBytes handles fragmented prefixes/payloads and consecutive frames. Invalid size is rejected before allocation. Malformed JSON, duplicate fields, invalid UTF-8, null envelopes, trailing JSON and mid-frame EOF close only the offending socket. Uncorrelatable malformed frames cannot receive a reliable structured reply. A clean EOF between frames closes normally.

```json
{"version":1,"kind":"REQUEST","type":"PING","requestId":"11111111-1111-1111-1111-111111111111","eventId":null,"payload":{},"ok":null,"error":null}
```

RESPONSE repeats type/requestId and includes ok=true with payload, or ok=false with error `{code,message}`. EVENT has eventId UUID, type and payload; requestId/ok/error are null. Unknown envelope/payload fields are rejected. version=1 is required. Request IDs are UUID strings. Required-but-unimplemented business types return a structured INVALID_TRANSITION after authentication and applicable role checks. No menu claims that these actions work.

| Implemented request | Payload | Authorization / result |
|---|---|---|
| PING | {} | Any socket; message=PONG |
| REGISTER_CITIZEN | username, displayName, password, optional contactNumber | Creates CITIZEN only; public user view; does not authenticate |
| LOGIN | username, password | Binds identity to socket; replaces previous socket; public user view |
| LOGOUT | {} | Authenticated; clears binding |
| WHO_AM_I | {} | Foundation extension; own public user view |
| LIST_TEAMS | {} | Operator sees four teams; responder sees its own team only; citizen forbidden |
| TEST_EVENT | {} | Foundation extension; operator only; private TEST_NOTIFICATION delivered only to requesting operator |

Public user view: id, username, displayName, role. Team view: id, name, serviceType, availability, revision, presence. Neither response contains password hash/salt, contact data or private notes. Identity, role, actor and team ownership are never accepted from a client payload.

Shared enums define all required request types: REGISTER_CITIZEN, LOGIN, LOGOUT, GET_SNAPSHOT, CREATE_INCIDENT, GET_INCIDENT, LIST_INCIDENTS, LIST_TEAMS, SET_PRIORITY, ASSIGN_TEAM, CANCEL_ASSIGNMENT, RESPOND_ASSIGNMENT, UPDATE_ASSIGNMENT_STATUS, CLOSE_INCIDENT, REJECT_INCIDENT, SET_TEAM_AVAILABILITY, PING. Only the implemented table above is operational.

Shared event types include INCIDENT_CREATED, INCIDENT_UPDATED, ASSIGNMENT_OFFERED, ASSIGNMENT_UPDATED, TEAM_UPDATED (future stages), plus SESSION_REPLACED and TEST_NOTIFICATION (operational foundation extensions).

Error codes: VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, TEAM_UNAVAILABLE, ACTIVE_ASSIGNMENT_EXISTS, INVALID_TRANSITION, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, UNSUPPORTED_VERSION, INTERNAL_ERROR. Before login only registration/login/PING are allowed. Wrong-role actions fail FORBIDDEN; unexpected internal failures return a generic message and log only exception class.

Registration strips username/display name/contact, preserves password exactly, accepts Unicode, validates username 3–40 code points, display name 1–80, password 8–128 Java characters, optional contact ≤40. Unknown privileged fields fail validation. Missing/invalid login fields and wrong credentials return the same generic UNAUTHENTICATED message.

Mutation contract for Member 2: canonical SHA-256 hash of type+payload, recursively sorted object keys; array order and value representations retained. Dedup scope user ID/request ID. Match returns original success; changed content throws IDEMPOTENCY_CONFLICT. Callback and processed-request row commit atomically. Failed changes roll back and are not cached. Do not use this helper for login/logout/registration; a replay must never authenticate another socket.

Client call waits ten seconds then reports confirmation pending with original ID retained in memory. It does not silently retry or claim failure. ConnectionManager exposes pendingIds/retry for later reconciliation. Durable pending input, reconnect, snapshot/event synchronization, revision application and heartbeat belong to Member 3. A disconnect currently fails pending futures; no exactly-once delivery claim is made.
