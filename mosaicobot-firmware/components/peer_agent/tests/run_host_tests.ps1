param([string]$IdfPath = $env:IDF_PATH, [string]$Compiler = "clang")
$ErrorActionPreference = 'Stop'
if (!$IdfPath) { throw 'Set IDF_PATH or pass -IdfPath.' }
$repo = (Resolve-Path "$PSScriptRoot/../../..").Path
$cjson = "$repo/managed_components/espressif__cjson/cJSON"
$out = "$repo/build/peer-host-tests"
New-Item -ItemType Directory -Force $out | Out-Null
& $Compiler -std=c11 -Wall -Wextra -Werror "$PSScriptRoot/test_peer_screen.c" -o "$out/peer_screen_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Screen host compilation failed' }
& "$out/peer_screen_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Screen host tests failed' }
& $Compiler -std=c11 -Wall -Wextra -Werror -I "$PSScriptRoot/host_include" -I "$IdfPath/components/esp_common/include" -I "$repo/third-party/esp-claw/components/common/mcp_mdns/include" -I "$repo/components/peer_core/include" -I "$cjson" "$PSScriptRoot/test_peer_server.c" "$repo/components/peer_core/src/peer_codec.c" "$cjson/cJSON.c" -o "$out/peer_server_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Host compilation failed' }
& "$out/peer_server_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Host tests failed' }
& $Compiler -std=c11 -Wall -Wextra -Werror -I "$PSScriptRoot/host_include" -I "$IdfPath/components/esp_common/include" -I "$repo/components/peer_agent/include" -I "$repo/components/peer_core/include" -I "$repo/third-party/esp-claw/components/common/settings/include" -I "$cjson" "$PSScriptRoot/test_peer_rpc.c" "$repo/components/peer_core/src/peer_codec.c" "$cjson/cJSON.c" -o "$out/peer_rpc_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'RPC host compilation failed' }
& "$out/peer_rpc_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'RPC host tests failed' }
& $Compiler -std=c11 -Wall -Wextra -Werror -I "$PSScriptRoot/host_include" -I "$IdfPath/components/esp_common/include" -I "$repo/components/peer_core/include" -I "$cjson" "$PSScriptRoot/test_peer_rpc_archive.c" "$repo/components/peer_core/src/peer_codec.c" "$cjson/cJSON.c" -o "$out/peer_archive_tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Archive host compilation failed' }
Push-Location $out
try {
    & "$out/peer_archive_tests.exe"
    if ($LASTEXITCODE -ne 0) { throw 'Archive host tests failed' }
} finally { Pop-Location }
