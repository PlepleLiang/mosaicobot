param(
    [ValidateSet('android','firmware')][string]$Target,
    [string]$IdfPath = $env:IDF_PATH,
    [string]$Python = 'python',
    [string]$Gspc = $env:GSPC_EXECUTABLE,
    [string]$IrisPython = "$PSScriptRoot/../esp-mosaico-vibe-custom/.tools/iris-host/Scripts/python.exe"
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($Target -eq 'android') {
    Push-Location "$root/mosaicobot-android"
    try {
        & ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
    } finally { Pop-Location }
} elseif ($Target -eq 'firmware') {
    if (-not $IdfPath) { throw 'Set IDF_PATH or pass -IdfPath' }
    $revision = & git -C $IdfPath rev-parse HEAD
    if ($revision -ne '7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe') { throw 'Incorrect ESP-IDF revision' }
    if (-not $Gspc -or -not (Test-Path $Gspc)) { throw 'Set GSPC_EXECUTABLE to GSPC 0.6.1' }
    if (-not (Test-Path $IrisPython)) { throw 'Prepare .tools/iris-host from the ESP-Iris requirements.lock first' }
    $IrisPython = (Resolve-Path $IrisPython).Path
    $env:ESP_IRIS_PYTHON = $IrisPython
    $env:GSPC_EXECUTABLE = $Gspc
    $env:MSYSTEM = $null
    $runner = "$root/esp-mosaico-vibe-custom/submodule/esp-mosaico-utils/mosaico-tools/skills/idf-low-noise-build/scripts/idf_low_noise_build.py"
    & $Python $runner --project "$root/mosaicobot-firmware" --idf-path $IdfPath doctor
    if ($LASTEXITCODE -ne 0) { throw 'SDK doctor failed' }
    & $Python $runner --project "$root/mosaicobot-firmware" --idf-path $IdfPath build --build-dir build-mosaicobot -D ESP_MOSAICO_BUILD_PROFILE=tools/cmake/mosaicobot_profile.cmake -D "ESP_IRIS_PYTHON=$IrisPython"
    if ($LASTEXITCODE -ne 0) { throw 'Firmware build failed' }
} else { throw 'Specify -Target android or firmware' }
