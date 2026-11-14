param([ValidateSet('server','client')][string]$Mode='client')
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$helper=Join-Path $PSScriptRoot 'project.ps1'
Push-Location -LiteralPath $repoRoot
try {
    $jar=Join-Path $repoRoot "application/$Mode/target/$Mode-1.0.0-SNAPSHOT.jar"
    if (-not(Test-Path -LiteralPath $jar)) { & $helper build }
    if ($Mode -eq 'server') { & $helper seed }
    & $helper $Mode
} finally { Pop-Location }
