param([switch]$WithoutRuntime)
$ErrorActionPreference='Stop'
$appRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $appRoot 'scripts/lan/Common.ps1')
$repoRoot=Split-Path -Parent $appRoot
$output=Join-Path $repoRoot 'DEMO-PACKAGES'
$stage=Join-Path $appRoot ('.local/ui-packaging/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $output,$stage | Out-Null
$serverJar=Get-DemoJar server;$clientJar=Get-DemoJar client
$runtime=$null
if(-not $WithoutRuntime){
    $java=Get-DemoJava;$jdk=Split-Path -Parent (Split-Path -Parent $java)
    $jlink=Join-Path $jdk 'bin/jlink.exe'
    if(-not(Test-Path -LiteralPath $jlink)){throw 'Portable GUI packaging requires a full Java 21 JDK with jlink. Use -WithoutRuntime if Java will be installed separately.'}
    $runtime=Join-Path $stage 'runtime'
    & $jlink --module-path (Join-Path $jdk 'jmods') --add-modules java.desktop,java.sql,java.naming,java.management,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if($LASTEXITCODE -ne 0){throw 'GUI runtime creation failed.'}
}
$suffix=if($WithoutRuntime){'JAVA21-REQUIRED'}else{'WINDOWS-X64'}
foreach($role in @('SERVER','CLIENT')){
    $name="EHN-UI-$role-$suffix";$folder=Join-Path $stage $name
    $desktop=Join-Path $folder 'scripts/desktop';$lan=Join-Path $folder 'scripts/lan'
    New-Item -ItemType Directory -Force -Path $folder,$desktop,$lan | Out-Null
    $module=$role.ToLowerInvariant();$jar=if($role -eq 'SERVER'){$serverJar}else{$clientJar}
    Copy-Item -LiteralPath $jar -Destination (Join-Path $folder "$module.jar")
    foreach($file in @('LaunchDesktop.ps1','LaunchDesktop.vbs')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $desktop}
    Copy-Item -LiteralPath (Join-Path $appRoot 'scripts/lan/Common.ps1') -Destination $lan
    $launchers=if($role -eq 'SERVER'){@('RUN-SERVER.cmd','RUN-LAN-SERVER.cmd','SHOW-NETWORK.cmd','ALLOW-LAN-FIREWALL.cmd','OPEN-SERVER.vbs')}else{@('RUN-CLIENT.cmd','RUN-LAN-CLIENT.cmd','CHECK-CONNECTION.cmd','OPEN-CLIENT.vbs')}
    if($role -eq 'SERVER'){Copy-Item -LiteralPath (Join-Path $appRoot 'scripts/lan/Firewall.ps1') -Destination $lan}
    foreach($launcher in $launchers){
        $text=Get-Content -LiteralPath (Join-Path $repoRoot $launcher) -Raw
        $text=$text.Replace('application\scripts\desktop\','scripts\desktop\').Replace('application\scripts\lan\','scripts\lan\')
        [IO.File]::WriteAllText((Join-Path $folder $launcher),$text,[Text.Encoding]::ASCII)
    }
    if($runtime){Copy-Item -LiteralPath $runtime -Destination (Join-Path $folder 'runtime') -Recurse}
    $note=@"
EMERGENCY HELP NETWORK - DESKTOP UI - $role

1. Extract ALL files to a writable local folder.
2. Keep the JAR, scripts and runtime together.
3. Windows x64 ZIP includes Java with Swing; no Java/Maven installation is needed.
4. Open RUN-$role.cmd. OPEN-$role.vbs avoids even the brief CMD startup flash.

SERVER: click Start server. Share the active Wi-Fi IP and TCP port (5050 default).
CLIENT: enter server IP/port, click Connect / check, then Sign in.
Use the same permitted Wi-Fi. 127.0.0.1 means THIS laptop; 0.0.0.0 is not a client destination.
3 laptops: A server + operator client; B citizen; C responder clients.
2 laptops: A server + operator; B citizen + responder windows.

Demo password: DemoPass123!
Users: operator; citizen1, citizen2; police1, ambulance1, fire1, fire2.
Each client window uses a different account. Same-account login replaces the older session.

Citizen: fill 3 fields and submit. Operator: select report row, Assign team, choose online/available team.
Responder: select offer, Accept, then Mark en route -> Mark arrived -> Mark completed.
Operator closes the finished report; citizen sees History. Live updates refresh tables automatically.
The details window shows authorized public / own timeline information.
Unconfirmed requests are retained in Saved actions; do not send duplicate new reports.

Server data: .local/data/emergency-ui.db. Restart from the SAME folder to keep history.
Client recovery: .local/client-ui/. Passwords are not saved in endpoint settings.
Optional server firewall helper allows this Java program, Private-network TCP and LocalSubnet only.
See UI-DEMO-TAMIL.md for setup, firewall notes, demo steps and recovery.

Academic fictional demo. Trusted LAN only; no TLS or real emergency agencies.
For other operating systems use that platform's Java 21 and the source/JAR desktop entry points.
"@
    Set-Content -LiteralPath (Join-Path $folder 'START-HERE.txt') -Value $note -Encoding UTF8
    $guide=Get-Content -LiteralPath (Join-Path $repoRoot 'docs/guides/UI-DEMO-TAMIL.md') -Raw -Encoding UTF8
    $guide=$guide.Replace('[UI verification results](../verification/UI-RESULTS.md)','UI verification results in the source ZIP')
    Set-Content -LiteralPath (Join-Path $folder 'UI-DEMO-TAMIL.md') -Value $guide -Encoding UTF8
    foreach($extra in @('WIFI-DEMO-TAMIL.md','EVALUATION-CHECKLIST.md')){Copy-Item -LiteralPath (Join-Path $repoRoot "docs/guides/$extra") -Destination $folder}
    $zip=Join-Path $output "$name.zip"
    if(Test-Path -LiteralPath $zip){throw "Package already exists: $zip. Rename it before rebuilding packages."}
    Compress-Archive -LiteralPath $folder -DestinationPath $zip -CompressionLevel Optimal
    Write-Host "Created $zip"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$source=Join-Path $output 'EHN-UI-SOURCE.zip'
if(Test-Path -LiteralPath $source){throw 'Source ZIP already exists. Rename it before creating a fresh one.'}
$archive=[IO.Compression.ZipFile]::Open($source,[IO.Compression.ZipArchiveMode]::Create)
try{
    $prefix=$repoRoot.TrimEnd('\')+'\'
    foreach($file in Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Force){
        $relative=$file.FullName.Substring($prefix.Length).Replace('\','/')
        if($relative -match '(^|/)(\.git|\.local|target|DEMO-PACKAGES|\.idea|\.metadata)(/|$)'){continue}
        if($file.Name -eq 'server.properties' -or $file.Name -match '\.db(-wal|-shm)?$|^hs_err_pid|^replay_pid'){continue}
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Optimal)|Out-Null
    }
}finally{$archive.Dispose()}
$hashes=Get-ChildItem -LiteralPath $output -Filter '*.zip'|Sort-Object Name|ForEach-Object{$hash=Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256;"$($hash.Hash.ToLowerInvariant())  $($_.Name)"}
Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Value $hashes -Encoding ASCII
Write-Host "Created $source"
