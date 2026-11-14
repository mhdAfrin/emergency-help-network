# Complete demonstration

Academic demonstration — no real emergency dispatch.

1. Build, then run the smoke helper for an automated demonstration using a real server and six console client JVMs. Logs/results are in the current verification report.
2. For a manual demonstration seed and start the server, then open clients for operator, citizen1, police1, ambulance1, fire1 and fire2. Default demo password is DemoPass123!.
3. Citizen chooses 3 and reports a fictional ROAD_ACCIDENT after reading the disclaimer. Optional coordinates must be supplied together. Copy the confirmed reference. Operator immediately receives INCIDENT_CREATED while waiting for menu input.
4. Operator lists/filters incidents (5), reads details (6), sets HIGH priority (7), lists service/availability/presence (3) and offers police1, ambulance1 and fire1 (8). Review current values/revisions and confirm each action.
5. Each responder sees only its own offer (5). Fire1 declines (7) with a private reason. The team becomes AVAILABLE and the incident remains ACTIVE. Operator offers fire2 instead. Police1, ambulance1 and fire2 accept (6).
6. Police1 sends EN_ROUTE (8). Stop/restart the server. Clients reconnect independently of console input and explicitly require login again. Log in and observe restored authorized service state and BUSY reservation. No team is automatically freed/reassigned.
7. Operator attempts premature closure (12) and receives INVALID_TRANSITION. Police progresses ARRIVED while other services remain ACCEPTED. Citizen details (5) display separate service/assignment states, never an aggregate all-arrived claim. Use T/A to page timelines/assignments.
8. Each accepted responder sends only the next valid progress (8) through COMPLETED. Operator closes with a public resolution (12) after every assignment is terminal and at least one completed. Citizen/operator histories show the closed report; responders see own assignment history (10) and own timeline (11). Operator-only notes remain private from citizens/unrelated teams.
9. Test public rejection with a separate never-assigned report. Eligible OFFERED/ACCEPTED cancellation is operator action 10; EN_ROUTE/ARRIVED cancellation must fail. Cancellation/decline permit a replacement only after the previous assignment is terminal.
10. If an action is confirmation pending, menu 90 shows its saved original ID/input and consumes a late result or retries unchanged after login. Restart a client mid-report to resume completed draft fields. No passwords are persisted or auto-replayed.
11. Logout/exit clients and stop the server. Restart and confirm saved histories. Run load to reproduce 20 authenticated sessions and the documented localhost event-latency samples. Perform separate-laptop LAN and physical prompt checks using the linked checklists.

All data is fictional. Direct commands and Eclipse launches use application/ as the working directory. The runtime ZIP can also be extracted and run with Java 21 without Maven.
