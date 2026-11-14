# LAN and troubleshooting

Localhost 127.0.0.1 connects on one laptop. On a permitted LAN, copy application/config/server.properties.example to application/config/server.properties, set server.host=0.0.0.0, and use the server laptop's active LAN IPv4 address (ipconfig) in remote clients. Port defaults to 5050. 0.0.0.0 is a bind address, not a client destination. Check the owner's firewall/private-network permission and Wi-Fi client isolation if unreachable.

Run clients as distinct accounts. Logging into the same account replaces the previous session. Teams must be ONLINE and AVAILABLE before an offer. A disconnected active team stays BUSY; reconnect and log in again to restore it. History lists terminal records, not open reports. Saved actions are under Account / Saved actions; do not resubmit an uncertain report as a new ID.

If startup says port in use, stop the earlier project's server before starting this copy. If login is missing, run seed from the same working directory/config as the server. A new checkout has a fresh DB; older accounts/reports stay in their original project folder. Do not run two servers against one DB.

For display problems use RUN-CLIENT.cmd or the helper, which sets UTF-8. ASCII separators avoid mojibake dashes. Colors are optional EHN_COLOR=1 in a supporting terminal; NO_COLOR disables them. The application cannot set your terminal font face/size.

Manual verification still required: two permitted laptops, real firewall/Wi-Fi conditions, physical typing while notifications arrive and Eclipse interaction. Record actual date, IP/port, OS/Java versions, users, report/offer/progress/closure/privacy/reconnect results and tester. Do not label localhost timing as LAN timing. TCP is unencrypted and this fictional demo does not contact real agencies.
