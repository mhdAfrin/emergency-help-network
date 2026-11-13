# Member 2 demonstration

Academic demonstration — no real emergency dispatch.

1. From the checkout root, run the helper with build, then smoke. The automated smoke starts its own isolated server and six actual console client JVMs; see the test report for actual results.
2. For manual use run seed, then server. Open separate terminals and launch client for operator, citizen1, police1, ambulance1, fire1 and fire2. Use localhost:5050 and DemoPass123!. Console input remains active while events print.
3. Citizen1 chooses 3, reads the disclaimer and reports a fictional ROAD_ACCIDENT. Enter address/description and either both coordinates or two blank values. Copy the confirmed UUID.
4. Operator receives INCIDENT_CREATED without polling. Choose 5 to filter reports by state/priority; 6 to inspect public details/timeline; 7 to set HIGH priority after reviewing the current revision.
5. Choose 3 to filter teams by service/availability/presence. Choose 8 three times to offer police1, ambulance1 and fire1 to the same incident. Review the incident/team values and confirm each offer. Busy/offline/unavailable teams cannot be selected successfully.
6. Each responder receives only its own ASSIGNMENT_OFFERED and can choose 5 to inspect the saved current offer. Own team view shows BUSY. Availability changes are refused while assigned. Acceptance/progress controls are intentionally absent.
7. Citizen1 chooses 5 and sees independent POLICE, AMBULANCE and FIRE_RESCUE states and a public timeline. Use A/T to page when offered. No private reason is returned.
8. Operator chooses 10, supplies incident ID and a displayed assignment UUID, enters a private cancellation reason, reviews current values and confirms. Only OFFERED/ACCEPTED may be cancelled. The team becomes AVAILABLE; the incident remains ACTIVE. Choose 8 and offer fire2 as replacement for the cancelled fire service.
9. Citizen submits a separate fictional duplicate report. Operator chooses 11 and rejects that never-assigned report with a public reason. Citizen receives the rejection update. A report with assignment history cannot be rejected.
10. To exercise timeout behavior use the PendingActionsTest controlled delayed server. In manual use, if a business request times out, choose 90 to inspect retained input and reconcile/retry its exact original ID. Do not create a new report as a blind retry.
11. Restart the server and log in again manually. Records and BUSY reservations persist; responders start OFFLINE until login. Automatic heartbeat, reconnect, snapshots, acceptance and completion/closure are Member 3 work.

Direct commands and Eclipse setup are in SETUP.md. Use application/ as the working directory for Eclipse/JAR launches. Separate-laptop permitted LAN operation and physical interactive prompt behavior must still be reviewed on the group's actual machines.
