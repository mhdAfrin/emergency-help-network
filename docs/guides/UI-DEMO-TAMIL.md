# Desktop UI மூலம் laptop demo

இந்த edition-ல் application terminal-க்குப் பதிலாக Java Swing windows-ல் இயங்கும். Reporting, assignment, Police / Ambulance / Fire progress, short IDs, authorization, pending actions, reconnect மற்றும் shared Wi-Fi அனைத்தும் அதே TCP server-ஐப் பயன்படுத்துகின்றன.

## தொடங்குவது

1. Server laptop-ல் **RUN-SERVER.cmd** double-click. **Server control** window திறக்கும்.
2. Port 5050 வைத்துக் கொண்டு **Start server** click. Status **Running** ஆகும் வரை காத்திருக்கவும்.
3. Wi-Fi IP dropdown-ல் சரியான adapter தேர்வு செய்யவும். **Copy IP** மூலம் IP copy செய்யலாம். VPN / VirtualBox IP வேண்டாம்.
4. ஒவ்வொரு client laptop-லும் **RUN-CLIENT.cmd**. Server IP மற்றும் port enter → **Connect / check** → username/password → **Sign in**.
5. `.cmd` தொடக்கத்தில் ஒரு சிறிய window flash இருக்கலாம்; தொடர்ந்து terminal தேவையில்லை. Flash-யும் வேண்டாமெனில் **OPEN-SERVER.vbs / OPEN-CLIENT.vbs** பயன்படுத்தலாம். Windows Script Host disabled என்றால் source application folder-ல் Java 21 `javaw -cp <jar> <desktop class>` முறை guide-ல் உள்ளது.

`RUN-LAN-SERVER.cmd` மற்றும் `RUN-LAN-CLIENT.cmd` இதே desktop windows-ஐத் திறக்கும் aliases. CHECK-CONNECTION.cmd client window-ஐத் திறக்கும்; அதில் Connect / check அழுத்தவும். SHOW-NETWORK.cmd server control window-ஐத் திறக்கும்.

## மூன்று laptops அமைப்பு

| Laptop | திறக்க வேண்டியது |
|---|---|
| A | Server control + operator client |
| B | citizen1 client |
| C | fire1 / police1 / ambulance1 clients தனித்தனி windows |

இரண்டு laptops என்றால் A-ல் server+operator; B-ல் citizen+responder. Friends-க்கு **DEMO-PACKAGES/EHN-UI-CLIENT-WINDOWS-X64.zip** அனுப்பவும். வேறு laptop server என்றால் **EHN-UI-SERVER-WINDOWS-X64.zip**. முழுவதும் Extract All செய்து பிறகே இயக்கவும். Windows x64 ZIP-ல் GUI-க்கு தேவையான Java runtime உள்ளது; Maven/JDK install தேவையில்லை. ARM/macOS/Linux-க்கு இந்த runtime பொருந்தாது; அந்த OS Java 21 + source JARs தேவை.

Same Wi-Fi / hotspot laptop-to-laptop TCP அனுமதிக்க வேண்டும். Internet தேவையில்லை. Server IP `0.0.0.0` அல்ல; actual Wi-Fi IP வேண்டும். `127.0.0.1` என்றால் current laptop. Server network profile/firewall குறித்து [Wi-Fi guide](WIFI-DEMO-TAMIL.md)-ன் network section பார்க்கவும். இந்த GUI edition-ன் application data source folder-ல் application/.local/data/emergency-ui.db; portable server package-ல் .local/data/emergency-ui.db. பழைய edition DB-கள் மாற்றப்படவில்லை.

## Demo accounts

அனைவருக்கும் password **DemoPass123!**. Roles-ஐ dropdown-ல் தேர்வு செய்யத் தேவையில்லை; server account-ன் role-க்கு ஏற்ப dashboard திறக்கும்.

| Username | Screen |
|---|---|
| citizen1 / citizen2 | Report incident, Open reports, History, Saved actions |
| operator | Open reports, Teams, History, Saved actions |
| police1 / ambulance1 / fire1 / fire2 | My assignments, My team, History, Saved actions |

ஒவ்வொரு client window-க்கும் வெவ்வேறு account. ஒரே account இரண்டு windows-ல் பயன்படுத்தினால் பழைய session replace ஆகும். Password local endpoint file-ல் save செய்யப்படாது.

## Lecturer முன் எளிய workflow

1. Server control-ல் **Running**, Wi-Fi IP, port, connected client count காட்டவும்.
2. Citizen **Report incident** tab-ல் category, address/landmark, description enter. **Submit report → OK**. 5-digit report reference உடன் Open reports table-ல் வரும்.
3. Operator-ன் **Open reports** table தானாக refresh ஆகும். கீழே **Live updates** காட்டும். ஒரு report row select → **Assign team** → online/available team தேர்வு → confirm.
4. Responder **My assignments** table-ல் offer வரும். Row select → **Accept offer** → optional note → OK → confirm.
5. அதே row-ல் அடுத்த valid state button மட்டும் active: **Mark en route → Mark arrived → Mark completed**. ஒவ்வொரு முறையும் note optional + confirm.
6. Citizen **Open reports → row select → View progress**. Public timeline மற்றும் service-wise states தெரியும்.
7. Operator row select → **Close report** → public resolution → confirm. எல்லா active service வேலைகளும் முடிந்திருக்க வேண்டும். Citizen **History** tab-ல் closed report பார்க்கலாம்.
8. Multi-service demonstration-க்கு அதே report-க்கு Police, Ambulance, Fire மூன்று teams assign செய்து independent progress காட்டவும்.

IDs type செய்ய வேண்டியதில்லை; table rows / dropdown choices உள்ள server references பயன்படுத்தப்படும். Tables sort செய்ய column heading click செய்யலாம். **Refresh** manual button உள்ளது; socket events வந்தால் dashboard தானாகவும் refresh ஆகும்.

## கூடுதல் controls

- Citizen draft typing தற்காலிகமாக local-ல் autosave; **Save draft** மூலம் explicit save. அடுத்த login-ல் மீட்கப்படும்.
- Operator **Priority** மூலம் priority மாற்றலாம். **More actions**-ல் unassigned report reject அல்லது offered/accepted assignment cancel; server விதிகள் இறுதி முடிவு.
- Responder **My team → Change availability**. Active assignment இருந்தால் team BUSY; server மறுக்கும் invalid change.
- **Saved actions**-ல் reply இழந்த original request-ஐ check/retry செய்யலாம். Duplicate புதிய request உருவாக்க வேண்டாம். Saved pending action இருந்தால் புதிய business mutation முதலில் தடுக்கப்படும்.
- **Account** மூலம் identity/server address; **Sign out** மூலம் அடுத்த user login.
- Server control-ல் **Stop server** அழுத்தினால் clients reconnect காத்திருக்கும். Same folder+port-ல் Start server; clients மீண்டும் sign in. Authorized snapshot current service state மீட்கும்.

## Project files

- common/.../desktop/DesktopTheme.java: shared colors/fonts/table/button styles.
- server/.../desktop/ServerDesktop.java: server control window, background start/stop, client/report counts.
- client/.../desktop/ClientDesktop.java: client lifecycle/button handlers/background tasks.
- client/.../desktop/DesktopSession.java: reusable protocol adapter; auth/revision/pending logic.
- client/.../desktop/view/: sign-in, role dashboard, report form, tables, detail dialog.
- scripts/desktop/: hidden Windows launch helpers using javaw.

Core business/network code stays in its existing packages. No browser, HTML server, JavaFX SDK or UI library dependency is needed. Background workers perform networking/database startup; Swing's UI thread draws the windows.

## Source build / direct GUI launch

BUILD-PROJECT.cmd builds/tests source; MAKE-DEMO-PACKAGES.cmd creates new GUI packages. These maintenance commands can show a terminal; normal RUN-*.cmd opens the desktop application. Rename old generated ZIPs before making new ones.

Java 21 installed, from source application folder:

```powershell
javaw -cp server/target/server-1.0.0-SNAPSHOT.jar lk.emergency.server.desktop.ServerDesktop
javaw -cp client/target/client-1.0.0-SNAPSHOT.jar lk.emergency.client.desktop.ClientDesktop
```

For a terminal-free launch on non-Windows systems use that platform's desktop Java launcher/app integration; its ordinary `java -cp ...` can also launch the same Swing classes. Old console entry points are retained for automated checks and optional diagnostics. Do not use old console-only ZIPs for a GUI demo.

## Rehearsal

Actual separate laptops, their firewall/router restrictions, screen scale/font rendering and lecturer workflow must be checked on the group's machines. [UI verification results](../verification/UI-RESULTS.md) records executed checks and remaining manual checks; [physical laptop checklist](EVALUATION-CHECKLIST.md) is for your rehearsal.
