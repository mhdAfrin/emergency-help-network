Set-StrictMode -Version Latest
$script:DemoApplicationRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Set-DemoEncoding {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [Console]::InputEncoding = $encoding
    [Console]::OutputEncoding = $encoding
    $global:OutputEncoding = $encoding
}

function Get-DemoJava {
    $candidates = @(
        (Join-Path $script:DemoApplicationRoot 'runtime/bin/java.exe'),
        (Join-Path $script:DemoApplicationRoot '.local/tools/jdk-21.0.13+9/bin/java.exe')
    )
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin/java.exe' }
    $installed = Get-Command java -ErrorAction SilentlyContinue
    if ($installed) { $candidates += $installed.Source }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $process = New-Object System.Diagnostics.Process
            $process.StartInfo.FileName = $candidate
            $process.StartInfo.Arguments = '-version'
            $process.StartInfo.UseShellExecute = $false
            $process.StartInfo.CreateNoWindow = $true
            $process.StartInfo.RedirectStandardError = $true
            $process.StartInfo.RedirectStandardOutput = $true
            try {
                [void]$process.Start()
                $versionText = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
                $process.WaitForExit()
                if ($process.ExitCode -ne 0) { continue }
            } finally { $process.Dispose() }
            if ($versionText -match 'version\s+"(\d+)') {
                if ([int]$Matches[1] -lt 21) { continue }
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }
    throw 'Java 21 or newer is required. Use the Windows x64 demo package with its bundled runtime, or install Java 21 and set JAVA_HOME.'
}

function Get-DemoJar([ValidateSet('server','client')][string]$Module) {
    $portable = Join-Path $script:DemoApplicationRoot "$Module.jar"
    if (Test-Path -LiteralPath $portable) { return $portable }
    $built = Join-Path $script:DemoApplicationRoot "$Module/target/$Module-1.0.0-SNAPSHOT.jar"
    if (Test-Path -LiteralPath $built) { return $built }
    throw "Missing $Module JAR. In the source project run BUILD-PROJECT.cmd first, or extract the complete demo ZIP."
}

function Get-DemoIPv4 {
    foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($adapter.OperationalStatus -ne 'Up' -or $adapter.NetworkInterfaceType -eq 'Loopback') { continue }
        $properties = $adapter.GetIPProperties()
        $gateway = @($properties.GatewayAddresses | Where-Object { $_.Address.ToString() -ne '0.0.0.0' })
        foreach ($entry in $properties.UnicastAddresses) {
            $address = $entry.Address
            if ($address.AddressFamily -ne 'InterNetwork' -or [Net.IPAddress]::IsLoopback($address)) { continue }
            if ($address.ToString().StartsWith('169.254.')) { continue }
            [pscustomobject]@{
                Adapter = $adapter.Name
                Type = $adapter.NetworkInterfaceType.ToString()
                Address = $address.ToString()
                Gateway = if ($gateway.Count) { $gateway[0].Address.ToString() } else { '(none)' }
                Wifi = $adapter.NetworkInterfaceType -eq 'Wireless80211'
            }
        }
    }
}

function Show-DemoNetwork([int]$Port) {
    Write-Host ''
    Write-Host 'CLIENT DESTINATION CANDIDATES' -ForegroundColor Cyan
    $addresses = @(Get-DemoIPv4)
    if (-not $addresses.Count) {
        Write-Host 'No active usable IPv4 adapter found. Connect Wi-Fi, then run SHOW-NETWORK.cmd again.' -ForegroundColor Yellow
        return
    }
    foreach ($entry in ($addresses | Sort-Object @{Expression='Wifi';Descending=$true},Adapter)) {
        Write-Host ("  {0,-18}  {1}:{2}  gateway {3}" -f $entry.Adapter,$entry.Address,$Port,$entry.Gateway)
    }
    Write-Host 'Use the active Wi-Fi adapter shared with the other laptops.'
    Write-Host 'Do not choose a VPN/VirtualBox adapter or 0.0.0.0. The IP can change when Wi-Fi changes.'
}

function Read-DemoPort([int]$Provided, [int]$Default = 5050) {
    if ($Provided -gt 0) { return $Provided }
    $value = Read-Host "Server TCP port [$Default]"
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    $number = 0
    if (-not [int]::TryParse($value, [ref]$number) -or $number -lt 1 -or $number -gt 65535) {
        throw 'Port must be a number from 1 to 65535.'
    }
    return $number
}

function Test-DemoHost([string]$ServerHost) {
    if ([string]::IsNullOrWhiteSpace($ServerHost)) { throw 'Enter the SERVER laptop Wi-Fi IPv4 address or hostname.' }
    if ($ServerHost -match '\s|://|/' -or $ServerHost -in @('0.0.0.0','::','[::]')) {
        throw 'Enter only the server IP/hostname, not http://, a port, or 0.0.0.0.'
    }
}

function Invoke-DemoJava([string]$Java, [string[]]$JavaArguments) {
    & $Java @JavaArguments
    if ($LASTEXITCODE -ne 0) { throw "Java command failed (exit $LASTEXITCODE)." }
}
