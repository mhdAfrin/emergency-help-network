# GUI edition Wi-Fi மற்றும் firewall குறிப்புகள்

இந்த edition-ஐ இயக்க [UI-DEMO-TAMIL.md](UI-DEMO-TAMIL.md) பார்க்கவும். RUN-SERVER.cmd / RUN-CLIENT.cmd இப்போது desktop windows திறக்கும். பழைய console-only packages பயன்படுத்த வேண்டாம்.

மூன்று laptops: A server+operator, B citizen, C responders. எல்லோரும் ஒரே அனுமதிக்கப்பட்ட Wi-Fi/hotspot-ல் இருக்க வேண்டும். Internet தேவையில்லை; device isolation உள்ள guest Wi-Fi-ல் clients ஒன்றையொன்று அணுக முடியாமல் இருக்கலாம்.

Server control-ல் active Wi-Fi IP தேர்வு செய்யவும்; VPN/VirtualBox IP வேண்டாம். Clients அதே server IP மற்றும் TCP port பயன்படுத்த வேண்டும். 127.0.0.1 என்பது current laptop; 0.0.0.0 என்பது bind address மட்டும். Wi-Fi மாறினால் IP மீண்டும் பார்க்கவும். Server IPv4-ஐ `ipconfig` மூலமும் பார்க்கலாம்.

Trusted Wi-Fi-ஐ Windows Settings-ல் Private என அமைக்க அனுமதி இருந்தால் மட்டும் அமைக்கவும். Server laptop-ல் **ALLOW-LAN-FIREWALL.cmd → Run as administrator**: GUI பயன்படுத்தும் குறிப்பிட்ட **javaw.exe** + inbound TCP port + Private profile + LocalSubnet மட்டும் அனுமதி. UI-க்கு EHN-Desktop-LAN-TCP என்ற தனி rule; பழைய console java.exe rule மாற்றப்படாது. இந்த helper firewall disable செய்யாது அல்லது network profile மாற்றாது. Norton/வேறு security software இருந்தால் தனியான அனுமதி தேவைப்படலாம். Campus-managed policy மீற வேண்டாம்.

முதல் launch-ல் Windows Security network permission prompt வரலாம். அதன் முடிவை laptop owner தேர்வு செய்ய வேண்டும்; இந்த development session அதை accept/cancel செய்யவில்லை. Trusted Private Wi-Fi-க்கு தேவையான அனுமதியை உங்கள் network policy படி review செய்யவும்; public-network access தேவையில்லை.

Custom port / cleanup, administrator PowerShell-ல் source project root-லிருந்து:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\lan\Firewall.ps1 -Port 5050
powershell -NoProfile -ExecutionPolicy Bypass -File .\application\scripts\lan\Firewall.ps1 -Port 5050 -Remove
```

முதல் command rule அமைக்கும்; இரண்டாவது demo முடிந்தபின் rule நீக்கும். Portable server package-ல் `scripts\lan\Firewall.ps1` path. Router port forwarding தேவையில்லை. Client laptops-ல் inbound server rule தேவையில்லை.

Connection failure: server Running? matching IP/port? correct Wi-Fi adapter? Private profile/firewall? VPN? Wi-Fi client isolation? “Connect / check” உண்மையான EHN PONG கேட்கிறது; open TCP port மட்டும் போதாது. ICMP ping fail ஆனாலும் TCP வேலை செய்யலாம். Source UI server uses .local/data/emergency-ui.db in application/; same folder/port restart செய்தால் records இருக்கும்.

Official references: [ipconfig](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/ipconfig), [TCP check](https://learn.microsoft.com/en-us/powershell/module/nettcpip/test-netconnection), [firewall rules](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule).
