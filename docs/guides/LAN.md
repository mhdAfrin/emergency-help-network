# LAN setup and troubleshooting

Read the [Tamil Wi-Fi guide](WIFI-DEMO-TAMIL.md) for the full laptop arrangement and role-by-role walkthrough. Use the [evaluation checklist](EVALUATION-CHECKLIST.md) to record actual physical-laptop results.

Server laptop: RUN-LAN-SERVER.cmd binds 0.0.0.0, seeds the same LAN database and starts one listener. SHOW-NETWORK.cmd lists actual active adapters. Clients use the server's Wi-Fi IPv4 plus matching port, never 0.0.0.0. 127.0.0.1 refers to the client laptop itself.

Client laptop: fully extract the Windows x64 client demo ZIP, then RUN-LAN-CLIENT.cmd. Its EHN PING/PONG preflight detects an incorrect service on an otherwise reachable port. Successful endpoints are saved locally; change the IP after changing networks. CHECK-CONNECTION.cmd runs just the preflight.

The optional administrator firewall helper permits this Java program, the selected inbound TCP port, Private profile and LocalSubnet. It does not change network profiles or configure third-party firewalls. Remove its named rule after the demo if no longer needed. Do not disable your firewall. Guest/campus Wi-Fi may isolate devices even when Internet access works.

Use distinct accounts for simultaneous clients. Logging into the same account replaces its old session. Responders must be ONLINE and AVAILABLE to receive an offer. A disconnected active team stays BUSY; reconnect/re-login restores the authorized assignment snapshot. History contains terminal reports, not open reports. Saved actions reconcile original requests; do not duplicate uncertain reports.

Keep the server folder and database across restart; do not run two servers against one DB. Stop old listeners or select a different port. New laptop packages do not contain old private databases or client journals. Colors are optional EHN_COLOR=1; NO_COLOR disables them.

TCP transport is for a permitted trusted fictional demo and has no TLS. No real emergency agencies are connected. Automated tests on one laptop, including its Wi-Fi interface, do not establish cross-laptop router/firewall reachability. Complete the actual rehearsal record.
