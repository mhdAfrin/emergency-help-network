param(
    [ValidateSet('build', 'seed', 'server', 'client', 'smoke')]
    [string]$Action = 'build',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectRoot '.local/tools/jdk-21.0.13+9'
if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) {
    $env:JAVA_HOME = $localJdk
}
if ($env:JAVA_HOME) {
    $javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'JAVA_HOME does not contain bin/java.exe.' }
    $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
} else {
    $javaExecutable = (Get-Command java -ErrorAction Stop).Source
}

$localTools = Join-Path $projectRoot '.local/tools'
if (Test-Path -LiteralPath (Join-Path $localTools 'maven-home')) {
    $env:MAVEN_USER_HOME = Join-Path $localTools 'maven-home'
}
$temporaryDirectory = Join-Path $projectRoot '.local/tmp'
New-Item -ItemType Directory -Force -Path $temporaryDirectory | Out-Null
$env:TEMP = $temporaryDirectory
$env:TMP = $temporaryDirectory

Push-Location -LiteralPath $projectRoot
try {
    if ($Action -eq 'build') {
        $mavenArguments = @('-B', 'clean', 'verify')
        if ($Offline) { $mavenArguments += '--offline' }
        $repository = Join-Path $localTools 'm2'
        if (Test-Path -LiteralPath $repository) { $mavenArguments += "-Dmaven.repo.local=$repository" }
        & (Join-Path $projectRoot 'mvnw.cmd') @mavenArguments
    } elseif ($Action -eq 'smoke') {
        & $javaExecutable 'scripts/testing/ProcessSmoke.java'
    } else {
        $module = if ($Action -eq 'client') { 'client' } else { 'server' }
        $jar = Join-Path $projectRoot "$module/target/$module-1.0.0-SNAPSHOT.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first: ./scripts/project.ps1 build' }
        if ($Action -eq 'seed') { & $javaExecutable -jar $jar --seed }
        else { & $javaExecutable -jar $jar }
    }
    if ($LASTEXITCODE -ne 0) { throw "Project command '$Action' failed (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}
