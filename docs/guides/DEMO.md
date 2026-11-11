# Member 1 demo

1. Build with wrapper; seed; start server as in README. Read the academic disclaimer.
2. Launch three actual client processes for operator, citizen1, fire1. Defaults connect localhost:5050.
3. Before login, Ping returns PONG. Register an additional fictional citizen and log in; identity must show CITIZEN.
4. Log in as operator. List four simulated teams. fire1 is ONLINE only after that team logs in. Citizen console has no team-list or dispatch action.
5. Operator chooses private test event. Observe TEST_NOTIFICATION and success response independently of the menu input loop. Other clients receive no test event.
6. Start a fourth client; log in as citizen1 again. Original citizen1 receives SESSION_REPLACED and disconnects. New client remains authenticated.
7. Log out; login again. Try a wrong password: generic invalid credentials. Try duplicate registration: validation error.
8. Stop and restart server, then log in as the newly registered citizen. Account persists. Presence begins offline. Reseed to show no duplicate users/teams.
9. Ctrl+C stops server; connected clients detect disconnect. Re-run wrapper verification for automated permission, frame, limit and transaction tests.

Keep each idle connection active with Ping before the foundation's 60-second socket timeout. Automatic heartbeat/reconnect and report → multi-service dispatch demonstration belong to later members. Timing and readability of partially typed input should be reviewed in the exact Eclipse/terminal setup used for submission.
