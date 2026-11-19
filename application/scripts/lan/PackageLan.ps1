param([switch]$WithoutRuntime)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$repoRoot = Split-Path -Parent $script:DemoApplicationRoot
$output = Join-Path $repoRoot 'DEMO-PACKAGES'
$staging = Join-Path $script:DemoApplicationRoot ('.local/lan-packaging/' + [guid]::NewGuid().ToString('N'))
$serverJar = Get-DemoJar server
$clientJar = Get-DemoJar client
New-Item -ItemType Directory -Force -Path $output,$staging | Out-Null
$runtime = $null
if (-not $WithoutRuntime) {
    $java = Get-DemoJava
    $jdk = Split-Path -Parent (Split-Path -Parent $java)
    $jlink = Join-Path $jdk 'bin/jlink.exe'
    if (-not (Test-Path -LiteralPath $jlink)) { throw 'Packaging a portable runtime requires a full Java 21 JDK with jlink. Use -WithoutRuntime for Java-installed packages.' }
    $runtime = Join-Path $staging 'portable-runtime'
    & $jlink --module-path (Join-Path $jdk 'jmods') --add-modules java.base,java.sql,java.naming,java.management,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw 'jlink runtime creation failed.' }
}
$suffix = if ($WithoutRuntime) { 'JAVA21-REQUIRED' } else { 'WINDOWS-X64' }
foreach ($role in @('SERVER','CLIENT')) {
    $name = "EHN-$role-$suffix"
    $folder = Join-Path $staging $name
    $scripts = Join-Path $folder 'scripts/lan'
    New-Item -ItemType Directory -Force -Path $folder,$scripts | Out-Null
    $module = $role.ToLowerInvariant()
    $jar = if ($role -eq 'SERVER') { $serverJar } else { $clientJar }
    Copy-Item -LiteralPath $jar -Destination (Join-Path $folder "$module.jar")
    foreach ($file in @('Common.ps1','LanDemo.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $scripts }
    $launchers = if ($role -eq 'SERVER') { @('RUN-LAN-SERVER.cmd','SHOW-NETWORK.cmd','ALLOW-LAN-FIREWALL.cmd') } else { @('RUN-LAN-CLIENT.cmd','CHECK-CONNECTION.cmd') }
    if ($role -eq 'SERVER') { Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Firewall.ps1') -Destination $scripts }
    foreach ($launcher in $launchers) {
        $content = Get-Content -LiteralPath (Join-Path $repoRoot $launcher) -Raw
        $content = $content.Replace('application\scripts\lan\', 'scripts\lan\')
        [IO.File]::WriteAllText((Join-Path $folder $launcher), $content.Replace("`r`n","`n").Replace("`n","`r`n"), [Text.Encoding]::ASCII)
    }
    if ($runtime) { Copy-Item -LiteralPath $runtime -Destination (Join-Path $folder 'runtime') -Recurse }
    $note = @"
EMERGENCY HELP NETWORK - $role PACKAGE

Extract the entire ZIP into a writable folder before opening a .cmd launcher.
Do not launch from inside the ZIP. Keep runtime/, scripts/ and the JAR together.
This package is $suffix. Windows portable packages include Java and its legal notices.
For JAVA21-REQUIRED packages, install Java 21 and set JAVA_HOME or PATH.

All laptops use the same permitted Wi-Fi/hotspot. Internet is not required after preparation.
Only the server laptop runs RUN-LAN-SERVER.cmd (port 5050 by default).
Clients run RUN-LAN-CLIENT.cmd and enter the SERVER laptop's active Wi-Fi IPv4 + port.
127.0.0.1 means the current laptop; 0.0.0.0 is a server bind address only.
Server: SHOW-NETWORK.cmd lists candidates. Prefer active Wi-Fi, not VirtualBox/VPN.
Server firewall: on a trusted PRIVATE network, run ALLOW-LAN-FIREWALL.cmd as admin if needed.
This rule only permits this Java program, TCP port, Private profile and LocalSubnet.
It does not change the network profile or disable security. Third-party firewalls may need separate approval.
Client CHECK-CONNECTION.cmd requires a real EHN protocol PONG, not just an open port.

Login: choose 2. Demo password: DemoPass123!
Citizen: citizen1 or citizen2. Operator: operator. Responders: police1, ambulance1, fire1, fire2.
Use different accounts for simultaneous windows. A duplicate login replaces the old session.
For 3 laptops: A server + operator client, B citizen client, C responder client(s).
For 2 laptops: A server + operator; B citizen + responder in separate windows.
The operator assigns an ONLINE + AVAILABLE team. The responder accepts, then EN_ROUTE,
ARRIVED and COMPLETED. Operator closes the incident; citizen sees history/progress.

The server alone owns .local/data/emergency-lan.db. Clients keep local recovery journals.
These folders appear on first run; never erase them to retry an uncertain request.
Stop server with Ctrl+C. Restart from the SAME folder and port, then log clients in again.
No source database, passwords from your previous project, or private client journals are included.
Academic fictional demo on a trusted LAN; transport has no TLS. No real agencies are contacted.

Full Tamil setup/demo/troubleshooting guide: DEMO-GUIDE-TAMIL.md (open in any text editor).
"@
    Set-Content -LiteralPath (Join-Path $folder 'START-HERE.txt') -Value $note -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/guides/WIFI-DEMO-TAMIL.md') -Destination (Join-Path $folder 'DEMO-GUIDE-TAMIL.md')
    $zip = Join-Path $output "$name.zip"
    if (Test-Path -LiteralPath $zip) { throw "Package already exists: $zip. Rename it before making another package." }
    Compress-Archive -LiteralPath $folder -DestinationPath $zip -CompressionLevel Optimal
    Write-Host "Created $zip"
}
$sourceZip = Join-Path $output 'EHN-EVALUATION-SOURCE.zip'
if (Test-Path -LiteralPath $sourceZip) { throw "Source package already exists: $sourceZip. Rename it before making another package." }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($sourceZip, [IO.Compression.ZipArchiveMode]::Create)
try {
    $repoPrefix = $repoRoot.TrimEnd('\') + '\'
    foreach ($file in Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Force) {
        $relative = $file.FullName.Substring($repoPrefix.Length).Replace('\','/')
        if ($relative -match '(^|/)(\.git|\.local|target|DEMO-PACKAGES|\.idea|\.metadata)(/|$)') { continue }
        if ($file.Name -eq 'server.properties' -or $file.Name -match '\.db(-wal|-shm)?$|^hs_err_pid|^replay_pid') { continue }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$hashes = Get-ChildItem -LiteralPath $output -Filter '*.zip' | Sort-Object Name | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $($_.Name)"
}
Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Value $hashes -Encoding ASCII
Write-Host "Created $sourceZip"
Write-Host 'Source ZIP excludes tools, runtime records, Git metadata, target output and demo ZIPs.'
