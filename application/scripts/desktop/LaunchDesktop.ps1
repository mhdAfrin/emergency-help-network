param([ValidateSet('server','client')][string]$Mode='client')
$ErrorActionPreference='Stop'
$appRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
try {
    . (Join-Path $appRoot 'scripts/lan/Common.ps1')
    $java=Get-DemoJava
    $javaw=Join-Path (Split-Path -Parent $java) 'javaw.exe'
    if(-not(Test-Path -LiteralPath $javaw)){throw 'The Java runtime is missing javaw.exe. Extract the complete UI package.'}
    $jar=Get-DemoJar $Mode
    $class=if($Mode -eq 'server'){'lk.emergency.server.desktop.ServerDesktop'}else{'lk.emergency.client.desktop.ClientDesktop'}
    $logRoot=Join-Path $appRoot '.local/ui-logs'
    New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
    $id=[guid]::NewGuid().ToString('N')
    $arguments=@('-cp',('"'+$jar+'"'),$class)
    Start-Process -FilePath $javaw -ArgumentList $arguments -WorkingDirectory $appRoot -WindowStyle Normal -RedirectStandardOutput (Join-Path $logRoot "$Mode-$id.out.txt") -RedirectStandardError (Join-Path $logRoot "$Mode-$id.err.txt") | Out-Null
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show("Could not open the desktop app.`n`n$($_.Exception.Message)`n`nBuild the source with BUILD-PROJECT.cmd or extract the complete UI ZIP.",'Emergency Help Network',0,16)|Out-Null
    exit 1
}
