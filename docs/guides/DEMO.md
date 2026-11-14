# Simple demonstration

1. Start server, then three clients: citizen1, operator, fire1. Login is startup choice 2. All demo passwords: DemoPass123!.
2. Citizen chooses 1, enters FIRE, a fictional landmark and description. Copy the five-digit "Incident reference". No GPS fields are requested.
3. Operator receives a live notification. Choice 1 lists existing reports; press Enter to leave filters blank. An operator who logs in later can still list saved reports.
4. Operator chooses 2, enters the incident reference, selects the online AVAILABLE Fire team reference and confirms y. A team reference is the five-digit number in its TEAM heading.
5. Fire1 receives an offer. Choose 2, then 1 to accept, optional note, y. For decline choose 2 then 2 and enter a private reason. Decline releases the reservation; operator can choose an online replacement.
6. Fire1 chooses 3 three times, confirming each transition: EN_ROUTE, ARRIVED, COMPLETED. Citizen choice 3 shows report, service progress and public timeline. Each service keeps its own status.
7. Operator chooses 3 then 3 to close, enters incident reference and a public resolution, then y. Closure refuses unfinished assistance and requires at least one COMPLETED assignment. Citizen choice 4 now shows the closed report.
8. For all three services, also login police1 and ambulance1 and offer them separate assignments on the same report. Progress each service independently. Unrelated teams must not see each other's private data.
9. Operator Manage: 1 details, 2 priority, 3 close, 4 reject never-assigned report, 5 cancel eligible OFFERED/ACCEPTED assignment. These remain fully functional nested actions.
10. Restart server while a responder is EN_ROUTE. Clients reconnect in the background, require login again and restore BUSY assignment state. Account / Saved actions reconciles uncertain actions using their original internal request IDs. Entering a report and exiting after a completed field preserves a draft for the next login.

Main menus have seven choices: five role actions, Logout (6), Exit (0). Grouped submenus use 0 to go back. Menus explain themselves with vertical labels; enum filters accept the displayed state/service words, or Enter for all. No terminal displays raw JSON or user-facing UUIDs.
