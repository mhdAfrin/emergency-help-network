CREATE TABLE users (
 id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE CHECK(length(username) BETWEEN 3 AND 40),
 display_name TEXT NOT NULL CHECK(length(display_name) BETWEEN 1 AND 80), password_hash BLOB NOT NULL,
 password_salt BLOB NOT NULL CHECK(length(password_salt)>=16), password_hash_iterations INTEGER NOT NULL CHECK(password_hash_iterations>0),
 role TEXT NOT NULL CHECK(role IN ('CITIZEN','OPERATOR','RESPONDER')), contact_number TEXT, created_at TEXT NOT NULL
);
CREATE TABLE teams (
 id TEXT PRIMARY KEY, responder_user_id TEXT NOT NULL UNIQUE REFERENCES users(id), name TEXT NOT NULL,
 service_type TEXT NOT NULL CHECK(service_type IN ('POLICE','AMBULANCE','FIRE_RESCUE')),
 availability TEXT NOT NULL CHECK(availability IN ('AVAILABLE','BUSY','UNAVAILABLE')), revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0)
);
CREATE TABLE incidents (
 id TEXT PRIMARY KEY, citizen_id TEXT NOT NULL REFERENCES users(id), category TEXT NOT NULL CHECK(category IN ('MEDICAL','ROAD_ACCIDENT','FIRE','CRIME','OTHER')),
 address TEXT NOT NULL CHECK(length(trim(address)) BETWEEN 1 AND 200), description TEXT NOT NULL CHECK(length(trim(description)) BETWEEN 1 AND 1000),
 latitude REAL, longitude REAL, priority TEXT NOT NULL CHECK(priority IN ('LOW','MEDIUM','HIGH')),
 state TEXT NOT NULL CHECK(state IN ('REPORTED','ACTIVE','CLOSED','REJECTED')), public_resolution_reason TEXT CHECK(length(public_resolution_reason)<=500),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL, closed_at TEXT, revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0),
 CHECK((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);
CREATE TABLE assignments (
 id TEXT PRIMARY KEY, incident_id TEXT NOT NULL REFERENCES incidents(id), team_id TEXT NOT NULL REFERENCES teams(id),
 service_type TEXT NOT NULL CHECK(service_type IN ('POLICE','AMBULANCE','FIRE_RESCUE')), assigned_by TEXT NOT NULL REFERENCES users(id),
 state TEXT NOT NULL CHECK(state IN ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED','COMPLETED','DECLINED','CANCELLED')), reason TEXT CHECK(length(reason)<=500),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0)
);
CREATE UNIQUE INDEX one_active_assignment_per_team ON assignments(team_id) WHERE state IN ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED');
CREATE UNIQUE INDEX one_active_service_per_incident ON assignments(incident_id,service_type) WHERE state IN ('OFFERED','ACCEPTED','EN_ROUTE','ARRIVED');
CREATE TABLE incident_events (
 id TEXT PRIMARY KEY, incident_id TEXT NOT NULL REFERENCES incidents(id), assignment_id TEXT REFERENCES assignments(id), actor_user_id TEXT NOT NULL REFERENCES users(id),
 event_type TEXT NOT NULL, public_message TEXT NOT NULL, internal_note TEXT CHECK(length(internal_note)<=500), created_at TEXT NOT NULL
);
CREATE TABLE processed_requests (
 user_id TEXT NOT NULL REFERENCES users(id), request_id TEXT NOT NULL, request_hash TEXT NOT NULL, response_json TEXT NOT NULL, created_at TEXT NOT NULL,
 PRIMARY KEY(user_id,request_id)
);
CREATE INDEX incidents_by_citizen ON incidents(citizen_id,created_at);
CREATE INDEX incidents_by_state_priority ON incidents(state,priority);
CREATE INDEX assignments_by_incident ON assignments(incident_id,created_at);
CREATE INDEX assignments_by_team ON assignments(team_id,created_at);
CREATE INDEX timeline_by_incident ON incident_events(incident_id,created_at);
CREATE INDEX teams_by_service ON teams(service_type,availability);
