param(
    [Parameter(Mandatory=$true)][ValidateSet('android','firmware')][string]$Target,
    [string]$Serial,
    [string]$DeviceId,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($Target -eq 'android') {
    if (-not $Serial) { throw 'Pass the serial from adb devices -l with -Serial' }
    if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME' }
    $adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
    & $adb devices -l
    & $adb -s $Serial install -r "$root/mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
} else {
    if (-not $DeviceId) { throw 'Pass a verified live Device ID with -DeviceId' }
    $env:PYTHONUTF8 = '1'
    Push-Location "$root/esp-mosaico-vibe-custom"
    try {
        & $Python mosaico.py iris status --all --json
        if ($LASTEXITCODE -ne 0) { throw 'Gateway status failed' }
        & $Python mosaico.py iris list --details --json
        if ($LASTEXITCODE -ne 0) { throw 'Device discovery failed' }
        & $Python mosaico.py iris claim --device-id $DeviceId --json
        if ($LASTEXITCODE -ne 0) { throw 'Device claim failed; coordinate ownership first' }
        & $Python mosaico.py iris device-status --device-id $DeviceId --json
        if ($LASTEXITCODE -ne 0) { throw 'Live identity check failed' }
        & $Python mosaico.py iris crash --archive --device-id $DeviceId --json
        if ($LASTEXITCODE -ne 0) { throw 'Crash archive failed; inspect before updating' }
        & $Python mosaico.py iris logs --device-id $DeviceId --snapshot
        if ($LASTEXITCODE -ne 0) { throw 'Log snapshot failed' }
        & $Python mosaico.py iris system-update --device-id $DeviceId --bundle ../mosaicobot-firmware/build-mosaicobot/edge_agent-system-update.irisfw --timeout 300 --json
        if ($LASTEXITCODE -ne 0) { throw 'Update failed/unknown; query original operation, do not retry blindly' }
        & $Python mosaico.py iris device-status --device-id $DeviceId --json
        if ($LASTEXITCODE -ne 0) { throw 'Post-update health check failed' }
    } finally { Pop-Location }
}
