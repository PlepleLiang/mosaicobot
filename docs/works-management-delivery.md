# Works 设备作品管理交付（2026-10-14）

## 已实现
- 交互稿 `device-works.js`：预装标记“预装 · 不可删除”，无删除入口，直接调用也被拒绝；按用户要求未重复校验原型。
- Android 设备应用保留 11 个系统入口，底部“Works 作品”读取真实设备目录，展示预装、部署版本及未注册残留包。刷新失败提示错误，不用手机本地记录伪造设备库存。
- 仅严格 `mb_` 不可变包版本可删除；预装作品在 UI、客户端和固件中保护。删除确认保留手机创作、Lua 源码和其他版本。
- 运行中目标先停止并等待清理，超时不继续删除；固件再次检查运行状态/job ID，并在删除期间拒绝该 ID 启动。成功后读回目录及 DATA 文件列表确认文件消失，再清理匹配的手机上传标记。
- 断连或取消时结果待同步，不自动重发写请求；旧固件提示更新，不回退到通用递归文件删除。

## 主要代码
Android：`projects/mosaicobot-android/app/src/main/java/dev/mosaicobot/` 下 `DeviceWork.kt`、`DeviceWorksScreen.kt`、`DeviceClient.kt`、`BotViewModel.kt`、`ApplicationsScreen.kt`、`AppCenterScreen.kt`；测试 `app/src/test/java/dev/mosaicobot/DeviceWorkTest.kt`。

固件：`projects/esp-mosaico-claw/components/http_server/http_server_works_api.c`、`WORKS_API.md`、`components/mosaic_ui/common/works_runtime.c` / `.h`；测试 `tools/tests/test_phone_works.py`、`test_works_management.py`。

接口：GET `/api/works` 无 id 返回 protocol 1 的 items（id/display_name/builtin/state）；POST 同路由增加 delete action，仅接受 mb_ ID。详细协议见 `WORKS_API.md`。

## 验证结果
- Android `:app:testDebugUnitTest :app:assembleDebug --console=plain`：exit 0，206 tests、0 failures/errors/skipped；其中新增 7 项删除测试覆盖预装、协议、停止顺序、停止失败/超时、目录及文件残留。
- 固件 `pytest tools/tests components/mosaic_ui/common/test_scene_assets.py -q --tb=short`：exit 0，14 passed、2 subtests passed，本次无排除项。
- pinned IDF commit `7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe`，doctor 确认 IDF 6.2.0、esp32s31 supported；自动 dependencies 约束仍 unresolved，使用交接明确指定的 SDK。
- low-noise `build-mosaicobot` + mosaicobot profile 增量构建：exit 0，`IDF LOW-NOISE BUILD: OK`，2 warnings。BIN 6,598,272 bytes。
- 构建最初因 bash MSYS 环境不受 IDF 支持失败，改用 PowerShell 正确清除继承的 MSYSTEM 后 doctor/build 通过；未换 SDK、未 fullclean。

产物：
- `projects/mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk`
- `projects/esp-mosaico-claw/build-mosaicobot/edge_agent.bin`、`edge_agent.elf`
- 固件完整日志：`D:/Android/Projects/my_workspace/projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261014-174140-build-35828/raw.log`

## 实机安装与验收（后续用户授权）
- 手机 `ZYPZ261A01M66A000UC`：`adb install -r` 返回 Success，保留原应用数据。
- Mosaico `4553502d49524953010030eda0f45ede`：官方 `system-update` operation `425db88e-0034-4a1c-8d6e-94ca1776ed97` 成功。normal Boot `9037896510073971405` → Vibe Boot `1711035007899762395` → normal Boot `12130342416432651510`；healthy/validated 均 true。
- 目标与实际 ELF SHA 均为 `122a4afd02ba58b6fa2f4d8355108896005e9ef28ae040682ff756a7cdd7f5f4`，bootloader/layout SHA 未变化。有效历史 core dump 已归档，未清除。更新日志：`projects/esp-mosaico-vibe/.codex-runs/mosaico/20261014T100721Z-system-update/raw.log`。
- 手机实际读取 20 个 Works（17 个已有部署版本、3 个预装），设备应用计数 31（另含 11 个系统入口）。Album、Dino、Flappy Bird 显示“预装 · 不可删除”，无删除按钮；截图已直接查看。
- 对原有“坦克大战 v1”仅打开删除确认并取消；读回确认原包仍存在，没有删除任何原用户作品。
- 从应用中心获取专用测试“一刻专注 v1”，通过手机确认部署并运行；包 ID `mb_ba789cb2e9bd4d8d_v1_934f61d9`，设备确认为 running。随后在真实 Works 列表确认“停止并删除”，读回确认包从 Works 目录与 DATA `/apps` 文件列表消失。
- 删除后的原 20 个 Works ID 集合与验收前完全一致。手机仍保留新增测试作品（手机共 8 个作品），详情显示相同 ID、96 行 Lua 和 SHA `934f61d909a4b79fa1af6a006b291c9e6ccd53533249551cd583569032c720e6`，可继续创作；未清除手机源码。
- 验收后实时 device-status 仍为同一 normal Boot、目标 SHA，stale=false、crash_count=0。

证据在 `docs/mosaicobot/validation/`：`works-device-installed-inventory.json`、`works-device-test-running.json`、`works-device-after-acceptance.json`、`works-device-files-after-acceptance.json`；截图 `works-device-updated.png`、`works-phone-preinstalled.png`、`works-phone-delete-confirm.png`、`works-phone-running-confirm.png`、`works-phone-delete-success.png`、`works-phone-source-retained.png`。

## 尚未做与已知边界
本次未做弱网、断连竞态、长期稳定性或完整设备触控启动器回归；未把 UI/目录删除成功等同于所有设备应用验收。预装服务端拒绝与超时/失败分支已有代码测试，未在实机发起预装删除或故障注入。未提交 Git，也未推进 FluidBox/Gamer。

registry 的目录删除不是事务；存储 I/O 失败可能留下部分目录，需刷新后重试，不宣称文件回滚。接口仍为可信 LAN 开发桥接，无新增配对认证；通用文件 API 外部修改不受此次 runtime 预约保护。当前连接绑定沿用局域网对象与地址，不是新增硬件身份鉴权。
