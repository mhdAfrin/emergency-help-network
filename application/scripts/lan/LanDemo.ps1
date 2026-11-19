param(
    [ValidateSet('server','client','check','info')][string]$Mode = 'client',
    [string]$ServerHost = '',
    [ValidateRange(0,65535)][int]$Port = 0
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
Set-DemoEncoding
Push-Location -LiteralPath $script:DemoApplicationRoot
try {
    Write-Host ''
    Write-Host '====================================================' -ForegroundColor Cyan
    Write-Host ' EMERGENCY HELP NETWORK - CLASSROOM WI-FI DEMO' -ForegroundColor Cyan
    Write-Host '====================================================' -ForegroundColor Cyan
    if ($Mode -eq 'info') {
        if ($Port -eq 0) { $Port = 5050 }
        Show-DemoNetwork $Port
        Write-Host 'Only the server laptop runs RUN-LAN-SERVER.cmd.'
        Write-Host 'Other laptops run RUN-LAN-CLIENT.cmd or CHECK-CONNECTION.cmd.'
        return
    }
    $java = Get-DemoJava
    if ($Mode -eq 'server') {
        $Port = Read-DemoPort $Port
        $jar = Get-DemoJar server
        $database = Join-Path $script:DemoApplicationRoot '.local/data/emergency-lan.db'
        $serverArguments = @('-jar',$jar,'--host','0.0.0.0','--port',"$Port",'--database',$database,'--max-connections','32')
        Show-DemoNetwork $Port
        Write-Host ''
        Write-Host "LAN bind: 0.0.0.0:$Port (all IPv4 adapters)"
        Write-Host "Shared server database: $database"
        Write-Host 'Keep this window open. Stop with Ctrl+C; use the same folder to preserve demo records.'
        Write-Host 'On a trusted private Wi-Fi network, allow this Java/TCP port through the server firewall.'
        Write-Host 'Optional: run ALLOW-LAN-FIREWALL.cmd as administrator; it only allows Private + LocalSubnet.'
        Write-Host 'Starting demo account preparation, then the listener...' -ForegroundColor Green
        Invoke-DemoJava $java ($serverArguments + '--seed')
        Invoke-DemoJava $java $serverArguments
        return
    }
    $jar = Get-DemoJar client
    $settingsFile = Join-Path $script:DemoApplicationRoot '.local/lan/client-endpoint.json'
    $saved = $null
    if (Test-Path -LiteralPath $settingsFile) {
        try {
            $saved = Get-Content -LiteralPath $settingsFile -Raw | ConvertFrom-Json
            Test-DemoHost ([string]$saved.host)
            if ([int]$saved.port -lt 1 -or [int]$saved.port -gt 65535) { $saved = $null }
        } catch { $saved = $null }
    }
    $explicitHost = -not [string]::IsNullOrWhiteSpace($ServerHost)
    if (-not $explicitHost) {
        $defaultHost = if ($saved) { [string]$saved.host } else { '' }
        $prompt = if ($defaultHost) { "SERVER laptop Wi-Fi IP [$defaultHost]" } else { 'SERVER laptop Wi-Fi IP (example 192.168.1.10)' }
        $ServerHost = (Read-Host $prompt).Trim()
        if (-not $ServerHost) { $ServerHost = $defaultHost }
    }
    $ServerHost = $ServerHost.Trim()
    Test-DemoHost $ServerHost
    if ($Port -eq 0 -and $explicitHost) { $Port = 5050 }
    else {
        $defaultPort = if ($saved) { [int]$saved.port } else { 5050 }
        $Port = Read-DemoPort $Port $defaultPort
    }
    if ($ServerHost -in @('127.0.0.1','localhost','::1')) {
        Write-Host 'Notice: this address connects to THIS laptop. Remote clients need the SERVER laptop Wi-Fi IP.' -ForegroundColor Yellow
    }
    Write-Host "Checking EHN server at ${ServerHost}:$Port ..." -ForegroundColor Cyan
    $endpointArguments = @('-jar',$jar,'--host',$ServerHost,'--port',"$Port")
    try { Invoke-DemoJava $java ($endpointArguments + '--check') }
    catch {
        Write-Host 'Connection check failed. Check: same permitted Wi-Fi, server window, IPv4/port, firewall, VPN and client isolation.' -ForegroundColor Yellow
        Write-Host 'A web browser URL is not used by this TCP application. Fix the connection and run this launcher again.'
        throw
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $settingsFile) | Out-Null
    @{host=$ServerHost;port=$Port} | ConvertTo-Json | Set-Content -LiteralPath $settingsFile -Encoding UTF8
    if ($Mode -eq 'check') { return }
    Write-Host 'Connection passed. Choose Login, then use a different account in each client window.' -ForegroundColor Green
    Write-Host 'Demo password: DemoPass123!  Users: operator, citizen1, citizen2, police1, ambulance1, fire1, fire2'
    Invoke-DemoJava $java $endpointArguments
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally { Pop-Location }
