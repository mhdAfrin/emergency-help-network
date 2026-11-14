# Separate-laptop LAN verification — manual, not claimed complete

This session has one machine and no authorized second laptop endpoint. Automated evidence is localhost TCP; a successful loopback test is not proof of Wi-Fi/firewall behavior across laptops.

1. Obtain permission to use the demonstration LAN and use fictional data only.
2. On server laptop, copy config/server.properties.example to config/server.properties inside application/. Set server.host=0.0.0.0 and choose the port (default 5050). Seed/start using the helper.
3. Run ipconfig and record the server laptop's active LAN IPv4 address. Do not enter 0.0.0.0 or 127.0.0.1 on the remote client.
4. Start the client on the second laptop with Java 21. Enter that server LAN IP and matching port. Check any firewall prompt with the laptop owner; avoid unauthorized rule changes. Wi-Fi client/device isolation may block communication.
5. Log in separate citizen/operator/responder accounts across laptops. Report a fictional incident; verify live notification, multi-service offers, valid progress and eligible closure.
6. Disconnect/reconnect one responder's network. Verify BUSY remains reserved, reconnect requires login, snapshot restores its assignment, and menu 90 reconciles any uncertain original ID.
7. Restart the server and confirm persistent records/closure/history. Check private notes never appear in citizen/unrelated-team consoles.

Record these actual results before final submission:

| Item | Fill after executing |
|---|---|
| Date/time and permission/context | Pending |
| Server/client laptop OS and Java versions | Pending |
| LAN type / server IP / port | Pending |
| Cross-laptop report-to-notification | Pending |
| Offers/progress/closure/privacy | Pending |
| Disconnect/re-login/snapshot/reservation | Pending |
| Firewall/device-isolation issues and fixes | Pending |
| Tester/reviewer and evidence location | Pending |

Do not enter measured LAN latency values without timing that network. The supplied load results use a single machine's loopback and one monotonic clock only.
