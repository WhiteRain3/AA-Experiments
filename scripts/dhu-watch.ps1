<#
.SYNOPSIS
  Keeps Google's Desktop Head Unit (DHU) in step with Android Auto on the phone.

  Starts the head unit server and DHU, then watches the phone. Whenever Android Auto restarts
  (for example after "Restart" or "Restart automatically after a change" in AA Experiments),
  it closes DHU, starts the head unit server again and reopens DHU.

  Close the DHU window, or press Ctrl+C here, to stop.

.EXAMPLE
  .\scripts\dhu-watch.ps1
  .\scripts\dhu-watch.ps1 -Touch                 # DHU's default touch-screen profile
  .\scripts\dhu-watch.ps1 -SdkPath C:\Android
  .\scripts\dhu-watch.ps1 -NoRoot                # do not start the head unit server (start it on the phone)

.NOTES
  Starting the head unit server from the PC needs root on the phone (it uses "su").
#>
param(
    [string]$SdkPath,
    [string]$Config = (Join-Path $PSScriptRoot 'dhu-rotary.ini'),
    [switch]$Touch,
    [switch]$NoRoot
)

$ErrorActionPreference = 'Stop'
$pkg = 'com.google.android.projection.gearhead'

# ---- locate tools ---------------------------------------------------------------------------
$candidates = @($SdkPath, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
                (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), 'C:\Android') | Where-Object { $_ }
$dhuExe = $null
foreach ($sdk in $candidates) {
    $exe = Join-Path $sdk 'extras\google\auto\desktop-head-unit.exe'
    if (Test-Path $exe) { $dhuExe = $exe; break }
}
if (-not $dhuExe) {
    Write-Error "desktop-head-unit.exe not found. Install 'Android Auto Desktop Head Unit Emulator' in the SDK Manager, or pass -SdkPath."
}
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "adb is not on PATH. Add <sdk>\platform-tools to PATH."
}
$devices = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' })
if ($devices.Count -ne 1) {
    Write-Error "Expected exactly one authorised adb device, found $($devices.Count). Check 'adb devices'."
}

# ---- helpers --------------------------------------------------------------------------------
function Get-AutoPid {
    # Main Android Auto process only; its helper processes have a ":name" suffix.
    $out = (adb shell "pidof $pkg" 2>$null | Out-String).Trim()
    if ($out) { return ($out -split '\s+' | Sort-Object) -join ' ' }
    return ''
}

function Get-HeadUnitService {
    # The service's class name changes between Android Auto versions, so look it up.
    $found = @(adb shell "dumpsys package $pkg" |
        Select-String -Pattern "$([regex]::Escape($pkg))/[A-Za-z0-9_.$]*HeadUnit[A-Za-z0-9_.$]*" -AllMatches |
        ForEach-Object { $_.Matches.Value } | Sort-Object -Unique)
    $preferred = $found | Where-Object { $_ -match 'Developer' } | Select-Object -First 1
    if ($preferred) { return $preferred }
    return ($found | Select-Object -First 1)
}

function Start-HeadUnitServer {
    if ($NoRoot) { return }
    if (-not $script:service) {
        Write-Warning 'Head unit server not found on the phone. Start it there: Android Auto settings > three-dot menu > Start head unit server.'
        return
    }
    adb shell "su -c 'am start-foreground-service -n $($script:service) || am startservice -n $($script:service)'" | Out-Null
}

function Start-Dhu {
    adb forward tcp:5277 tcp:5277 | Out-Null
    $arguments = @()
    if (-not $Touch) { $arguments = @('-c', "`"$Config`"") }
    if ($arguments.Count -gt 0) {
        return Start-Process -FilePath $dhuExe -ArgumentList $arguments -WorkingDirectory (Split-Path $dhuExe) -PassThru
    }
    return Start-Process -FilePath $dhuExe -WorkingDirectory (Split-Path $dhuExe) -PassThru
}

function Stop-Dhu($process) {
    if ($process -and -not $process.HasExited) {
        try { $process.Kill() } catch { }
        try { $process.WaitForExit(3000) | Out-Null } catch { }
    }
}

# ---- main loop ------------------------------------------------------------------------------
$script:service = if ($NoRoot) { $null } else { Get-HeadUnitService }
if ($script:service) { Write-Host "Head unit server: $($script:service)" }

Write-Host 'Starting head unit server and DHU...'
Start-HeadUnitServer
Start-Sleep -Milliseconds 1500
$lastPid = Get-AutoPid
$dhu = Start-Dhu
Write-Host 'Watching the phone. Close the DHU window or press Ctrl+C to stop.'

try {
    while ($true) {
        Start-Sleep -Milliseconds 1000

        if ($dhu.HasExited) {
            # DHU closed while Android Auto kept running: you closed the window. Stop.
            if ((Get-AutoPid) -eq $lastPid) { Write-Host 'DHU was closed. Stopping.'; break }
        }

        $current = Get-AutoPid
        if ($current -ne $lastPid) {
            Write-Host "$(Get-Date -Format HH:mm:ss) Android Auto restarted. Reopening DHU..."
            Stop-Dhu $dhu
            Start-Sleep -Milliseconds 1200
            Start-HeadUnitServer
            Start-Sleep -Milliseconds 1500
            $lastPid = Get-AutoPid     # read after the server start, which itself starts Android Auto
            $dhu = Start-Dhu
        }
    }
} finally {
    Stop-Dhu $dhu
    adb forward --remove tcp:5277 2>$null | Out-Null
}
