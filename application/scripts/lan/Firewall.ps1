[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateRange(1,65535)][int]$Port = 5050,
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$ruleName = "EHN-Desktop-LAN-TCP-$Port"
try {
    $operation = if ($Remove) { 'Remove this demo firewall rule' } else { 'Allow inbound TCP for this Java program, Private profile and LocalSubnet only' }
    if ($PSCmdlet.ShouldProcess($ruleName, $operation)) {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            throw 'Run ALLOW-LAN-FIREWALL.cmd (or this PowerShell command) as administrator on the SERVER laptop only.'
        }
        $existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
        if ($Remove) {
            if ($existing) { $existing | Remove-NetFirewallRule }
            Write-Host "Removed demo rule (if present): $ruleName"
            return
        }
        $java = Get-DemoJava
        $guiJava = Join-Path (Split-Path -Parent $java) 'javaw.exe'
        if (-not (Test-Path -LiteralPath $guiJava)) { throw 'The desktop Java runtime is missing javaw.exe.' }
        $java = $guiJava
        if ($existing) {
            $existing | Set-NetFirewallRule -Enabled True -Profile Private -Direction Inbound -Action Allow -EdgeTraversalPolicy Block
            $existing | Get-NetFirewallApplicationFilter | Set-NetFirewallApplicationFilter -Program $java
            $existing | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort $Port
            $existing | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet
        } else {
            New-NetFirewallRule -Name $ruleName -DisplayName "Emergency Help Network desktop TCP $Port" -Direction Inbound -Action Allow -Protocol TCP -LocalPort $Port -Program $java -Profile Private -RemoteAddress LocalSubnet -EdgeTraversalPolicy Block | Out-Null
        }
        Write-Host "Allowed Private-network LocalSubnet TCP $Port for: $java"
        Write-Host 'This does not change a Public Wi-Fi network to Private or disable any firewall.'
        Write-Host "After the demo: powershell -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Port $Port -Remove"
    }
} catch { Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
