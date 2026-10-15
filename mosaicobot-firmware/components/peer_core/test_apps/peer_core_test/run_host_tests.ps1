param([string]$IdfPath = $env:IDF_PATH, [string]$Compiler = "clang")
$ErrorActionPreference = 'Stop'
if (!$IdfPath) { throw 'Set IDF_PATH or pass -IdfPath (only Unity sources are used).' }
$repo = (Resolve-Path "$PSScriptRoot/../../../..").Path
$unity = "$IdfPath/components/unity"
$cjson = "$repo/managed_components/espressif__cjson/cJSON"
$core = "$repo/components/peer_core"
$out = "$repo/build/peer-host-tests"
New-Item -ItemType Directory -Force $out | Out-Null
& $Compiler -std=c11 -Wall -Wextra -Werror -DCONFIG_IDF_TARGET_LINUX=1 -include "$unity/include/unity_test_runner.h" -I "$PSScriptRoot/host_include" -I "$unity/unity/src" -I "$unity/include" -I "$cjson" -I "$core/include" "$PSScriptRoot/host_runner.c" "$PSScriptRoot/main/test_peer_core.c" "$core/src/peer_codec.c" "$core/src/peer_registry.c" "$core/src/peer_pairing.c" "$unity/unity/src/unity.c" "$cjson/cJSON.c" -o "$out/peer_core_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Host compilation failed' }
& "$out/peer_core_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Host tests failed' }
