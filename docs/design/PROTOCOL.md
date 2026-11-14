# Protocol and authorization

Transport remains framed UTF-8 JSON, maximum payload 65,536 bytes. Request/success/error envelopes and DTOs live in common/. Request IDs remain UUIDs for durable unchanged retry; they are internal. User-facing entity references are five-digit strings in a new database. Console rendering does not change structured wire data.

Anonymous requests: REGISTER_CITIZEN, LOGIN, PING. All signed-in roles: WHO_AM_I, LOGOUT, authorized GET_SNAPSHOT. The production menu exposes registration/login plus role actions; heartbeat and diagnostics are not menu clutter.

Citizen: CREATE_INCIDENT, own LIST_INCIDENTS and GET_INCIDENT. Reporting uses category/address/description; the UI supplies no coordinates. Operator: LIST_TEAMS, LIST_INCIDENTS, GET_INCIDENT, SET_PRIORITY, ASSIGN_TEAM, CANCEL_ASSIGNMENT, REJECT_INCIDENT and CLOSE_INCIDENT. Responder: own LIST_TEAMS/LIST_ASSIGNMENTS/GET_ASSIGNMENT, SET_TEAM_AVAILABILITY, RESPOND_ASSIGNMENT, UPDATE_ASSIGNMENT_STATUS.

Mutations use expected revisions and a per-user request-ID/content hash. Exact replay returns the saved committed result; changed payload conflicts. Events are sent only after commit and never duplicated for replay. Durable pending actions save before sending, are user/server scoped and retry the original envelope after explicit re-login.

INCIDENT_CREATED routes to connected operators. INCIDENT_UPDATED and assignment progress route to authorized incident owner/operator; ASSIGNMENT_OFFERED goes to its own team. Private cancellation/decline/progress notes remain limited to authorized operator/own projections.

Snapshot pages are socket-owned, frozen and bounded with one server epoch/sequence watermark. Login subscriptions precede snapshot creation. A 1,000-event client buffer and revision checks prevent stale application; 5,000 active combined records and a 30-second lease bound snapshot work. Terminal history is independently paginated. These are prototype bounds, not production guarantees.
