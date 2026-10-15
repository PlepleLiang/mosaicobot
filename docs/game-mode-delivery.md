# 横屏通用游戏手柄 · 首版交付记录

更新：2026-10-14。状态：输入过期/会话断开分离修复已完成单元测试、Android 覆盖安装和 Mosaico 官方固件更新；跑酷 v3 单独保留修复版，v2 不覆盖。未进行真实模型生成，完整玩法与手感由用户手验。之前双操作生成、连接修复与坦克操控的证据单独保留在下方。

[交互方案](game-mode.md) · [主设计稿](index.html?view=game) · [固件接口说明](../../projects/esp-mosaico-claw/components/lua_module_gamepad/README.md)

## 当前修复：2026-10-14 输入过期与会话断开分离

原因：旧固件在 350ms 输入过期时同时把 Lua connected 置 false；手机则按 2.5 秒判断会话失联。生成规则要求 connected 从 true 变 false 时正式暂停，导致手机保持就绪但游戏反复暂停。此前跑酷 v2 与设备源码一致，11:07–11:10 手机同一会话持续确认输入；没有逐次超时记录，不能据此归因每次暂停。

本轮修复：

- 固件 connected 代表已同步会话，input_active 代表输入时效。350ms 仍清空按键、轴与边沿，但不改变已同步会话；2.5 秒或显式释放/关闭才失联。安全归零保留最后物理按键基线，恢复时仍按住 Start 不会合成新 pressed。
- Lua 对短暂过期只临时冻结模拟、显示 PAD SIGNAL WEAK；继续绘制、轮询与计时更新，触屏可接管。新鲜输入恢复不解除正式暂停，真正断线仍暂停一次。
- 手机增加延迟 ACK 与 Start 边沿诊断日志；固件区分 input stale / resumed / session lost；跑酷记录 Start / touch / session lost 的暂停原因。ACK 延迟是诊断线索，不等于测得上行输入年龄。
- Agent 生成规则和生成专用检查增加 input_active，禁止从 held 重建离散按键边沿。仅检查结构，仍不声称任意生成游戏已通过玩法验收。
- 直接更新应用中心三款游戏的共用循环，不升级中心版本、不覆盖已获取副本。“极速跑酷”单独保存 v3 修复版，保留 v2 和其他作品；不修改跑酷的场景、碰撞、积分等玩法逻辑。

验证：Android 199 项单元测试全部通过；固件 11 项测试通过、1 项因此前已确认的 Windows ids.exe 安全策略限制排除，另有 2 个子测试通过。新增测试执行真实固件状态机（模拟时钟/RTOS/Lua API）和仓库手写游戏共用循环，覆盖 350ms / 2.5s 边界、重复序号、短超时按住 Start、真正释放重按、触屏接管及同时按暂停只切换一次。单元测试不执行模型 Lua；跑酷 v3 在部署前通过语法和结构检查，随后仅进行下述设备启动/连接检查。

交付状态：Android 已覆盖安装；跑酷 v3 已通过手机正常流程部署并显示“设备运行中”，v2 与其他 4 个作品保留。

- [设备启动画面](../../projects/esp-mosaico-claw/.codex-runs/gamepad-input/runner-v3-ready.png)已直接查看，显示 SUPER PIXEL RUN / PLAY；截图 Device ID 与新 Boot ID 一致。
- 手机自动连接手柄：11:46:27.844 就绪，至 11:47:08.081 同 epoch=3 已确认 1200 帧，无该区间自动断开。[手机日志](../../projects/esp-mosaico-claw/.codex-runs/gamepad-input/android-runner-v3.log)。这只验证待开始状态的连接，不是实际跑酷操控或注入弱网测试。
- 交付时手机留在“控制已就绪”，设备停在游戏待开始界面，按 A 可开始。手验重点：进入手柄不反复暂停；Start 长按只触发一次；短暂延迟恢复不解除手动暂停；真正断线后可用触屏继续。

- 正确 build-mosaicobot 配置增量构建成功，exit 0、2 条警告：[构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261014-114039-build-46792/raw.log)。默认 build 产物曾被契约检查拦下，未写入设备；没有修改分区来绕过检查。
- 官方 system-update：succeeded / healthy=true / validated=true，operation 92a83da5-27ae-4e82-b441-96caca6cf15d。[更新日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261014T034239Z-system-update/raw.log)。bundle 模式仍使用工具默认 projects/snake Gateway 名称空间，实际写入并验证的是 edge_agent，不是 snake；查询该 operation 使用 --project projects/snake。
- 同一 Device ID 4553502d49524953010030eda0f45ede，Boot 6583509893975638076 → 10248673715909024318（Vibe）→ 1546457411718506444。ELF / 设备 SHA-256：6e79443ed58965c500d927c4ff0b51012f8d4b3751ae874222b927ed52ba9b1b。
- [更新后实时状态](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261014T034429Z-device-status/raw.log)：normal / edge_agent，stale=false，crash_count=0。bootloader、完整分区表和 ui_apps 哈希保持不变；包不包含用户 NAND / NVS / Vibe 固件。更新前已归档历史 core dump，不将其算作本轮崩溃。
- Android APK SHA-256：c3265fb2fe6e13445ecc93ea82a96105b9407815ef97531cbfd77796a115d116。跑酷 v3 SHA-256：62719551f26de103c1b4a23a270ed1b61f897248da4f11b936cde468e629c8be；包名 mb_a350510230114d80_v3_62719551。手机修改前备份为私有 files/works-before-input-fix.json。

## 历史交付：2026-10-14 自由创作默认双操作

- 手机 Agent 默认按“触屏 / 手机手柄”生成或编辑游戏，不必在意图中额外强调手柄。明确仅触屏的需求及非游戏工具为例外。约束覆盖独立触屏玩法、共享动作状态、标准输入映射、失联暂停和重连不重置。
- 新增 GeneratedGamePolicy：只检查生成/编辑结果的交互声明及控制代码结构，剔除注释/字符串中的伪调用。缺少控制能力交回已有完整修复；续写返回完整替代包也检查。仍使用有界重试，失败保留原需求和作品。
- 分类由模型声明，结构检查不能证明实际玩法、可达性或触控区域正确，不执行模型 Lua。旧作品读取/导入/部署不受新检查影响；应用中心源码和版本、现有作品、Key 及固件均未改动。
- Android **193 项单元测试通过，0 失败 / 0 错误 / 0 跳过**，debug APK 构建成功。新增 18 项覆盖默认协议、仅触屏例外、回合输入、遗漏触控/手柄的修复、续写替代包、旧作品兼容及失败不返回新包。
- ADB install -r 成功，已覆盖安装到 PZ261A / ZYPZ261A01M66A000UC，未卸载或清除数据。构建产物：[app-debug.apk](../../projects/mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk)；[单测报告](../../projects/mosaicobot-android/app/build/reports/tests/testDebugUnitTest/index.html)。
- 本轮没有调用真实模型、没有刷新 Mosaico 固件或运行设备游戏，也未跑 Lint/UI 自动化。建议手验：直接生成贪食蛇 → 不连手机先触屏玩 → 连接手柄操作 → 断开手柄后触屏恢复 → 重连不重开；另测“仅触屏游戏”及旧游戏继续创作。

## 历史交付：2026-10-13 20:41 连接稳定性与低延迟修复

### 实际根因与修复

- 原手机已经握手成功，但热点下回执常延迟 0.6 秒；350ms 回执超时和 16 帧积压保护会主动拆掉会话，自动重连随即造成连接页和手柄页循环。并非 ALLOW 或请求权限问题。
- 手柄 socket 开启 TCP_NODELAY。手机限制重复心跳的未确认帧数为 8，为即时按键边沿保留余量；2.5 秒才判定输入会话失联。
- 设备仍在 350ms 无新有效输入时归零，并让 Lua 暂停，但保留会话令牌和已接收序号；2.5 秒才撤销控制权。保留退出、后台、游戏变化和非法包保护。
- 网络探测确认原链路有 300–450ms 延迟峰值。游戏运行期间临时关闭 Mosaico Wi-Fi modem sleep，结束时恢复原模式，不修改 NVS 电源配置。实机日志确认原模式为 1，已切换到 none。
- 应用中心直接修改现有 v2 预置源码，不再发布 v3/v4。已经获取的副本（包括这次测试用的旧 v3 坦克）原样保留，不删作品，不清 App 数据。

### 实机验证（不是仅检查 waiting_game）

- ADB 操作真实手机连接真实 Mosaico：20:38:36.979 就绪，20:41:11.072 主动退出；同一个会话连续约 154 秒，已确认 4,602 帧，无自动断开。随后重新进入，于 20:41:15.327 再次就绪，无设备批准步骤。
- 已用手机验证 A 开始、方向键移动、R1 射击、左模拟摇杆移动、Start 暂停/继续；设备截图分别显示坦克移到右侧、右侧砖墙被击破、摇杆向左后坦克位置变化。没有停止或重新部署游戏来伪造输入效果。
- PC 使用产品 /ws/gamepad 通道测得 19 个回执平均 8.1ms、最大 40.4ms；这是该次样本，不是所有网络的延迟保证。手机通道首次打开至就绪 58ms，后续重连 436ms。
- 人为停发 650ms 后同会话继续接受 seq=21；停发 2,800ms 后返回 released，验证短延迟保会话及真正失联释放。
- 手机目前留在已就绪手柄，坦克暂停；按 Start 可继续。临时屏幕旋转设置已恢复原 free 状态。

证据：[手机会话日志](../../projects/esp-mosaico-claw/.codex-runs/gamepad/android-final.log) · [原问题日志](../../projects/esp-mosaico-claw/.codex-runs/gamepad/android-before-latency-fix.log) · [协议检查](../../projects/esp-mosaico-claw/.codex-runs/gamepad/wire-verification.log) · [手机画面](../../projects/esp-mosaico-claw/.codex-runs/gamepad/final-phone.png) · [方向移动](../../projects/esp-mosaico-claw/.codex-runs/gamepad/final-moved-paused.png) · [射击后](../../projects/esp-mosaico-claw/.codex-runs/gamepad/final-fired-paused.png) · [摇杆移动后](../../projects/esp-mosaico-claw/.codex-runs/gamepad/final-stick-paused.png)。

### 构建与安装

- Android 175 项现有单测通过（包括预置 v2 / 无 ALLOW 的补充断言），构建及 ADB install -r 成功。APK SHA-256：1089dc399fdb4b289b8b3099c34e8ad58eda8325cc4ecf19e10f90150adba9f0。
- 固件现有测试 10 通过、1 因已知 Windows ids.exe 安全策略跳过，未绕过；低噪声构建成功，1 条警告。[构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-203209-build-15812/raw.log)。
- 最终官方更新 succeeded / healthy=true / validated=true，operation c6cbd58d-acc6-4216-a42a-7bff318860d8。[更新日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T123516Z-system-update/raw.log)。
- 同一 Device ID 4553502d49524953010030eda0f45ede，Boot 1309331602801817850 → 18049780180988845518（Vibe）→ 6583509893975638076。最终设备 ELF SHA-256：2d430042467947682ad0baef1d7a57a7ec192c31d78fe7326a868c3c5d7836d2。
- [最终实时状态](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T123731Z-device-status/raw.log)：normal / edge_agent，stale=false，crash_count=0；后续游戏截图的 Device ID / Boot ID 一致。分区布局、bootloader 和用户 NAND / NVS 保留。

以下为历史交付，不代表当前版本或验证状态。

## 历史交付：2026-10-13 18:32 自动连接版

按用户最新确认，移除请求控制、六位编号和 Mosaico ALLOW / DENY。下方此前的物理确认流程均为历史记录，不再适用。

使用：先运行已获取的坦克 / 飞机 / 赛车 v2 或 v3，再进入「应用 → 我的创作 → 让手机，成为手柄」。自动检查通道、游戏和首次输入回执，通过后直接控制。未运行游戏则显示等待；另一手机占用时等待释放，不抢占。无需重新获取已保存的 v2；新的应用中心源码去掉确认代码，独立发布 v3，原 v1/v2 及用户作品不覆盖。

### 变更与同步保护

- 固件在有效轮询且游戏可用时自动分配会话，不再向 Lua 返回 pending；approve 仅保留旧包兼容的无操作接口。
- 移除手机请求按钮和编号界面。前台自动检查、重连；手柄菜单内、后台或退出后不继续自动接入。
- 首次同步与运行断线保护分离：首次允许 2.5 秒接收输入，手机收到就绪状态立即发全零输入，首次序号回执后才启用按钮。避免沿用原 ALLOW 后 350ms 的首次输入窗口；正常运行仍保持 350ms 超时释放。
- Lua v3 和 Agent 生成协议删除允许/拒绝面板。主设计稿、交互文档和原型现有检查同步移除旧审批流程。
- 明文可信 LAN，任何可达客户端可能占用手柄；会话令牌仅用于拒绝旧包，不是用户鉴权。

### 构建、安装与验证

- Android 175 项现有单元测试通过（0 失败、0 错误），debug 构建成功；ADB install -r 已覆盖安装至 PZ261A / ZYPZ261A01M66A000UC，未清除数据。APK SHA-256：5188f1c09a88d24ccccd456efbc9a48fb759d746a28f131c986a90be5cfa9294。
- 固件现有测试 10 项通过、1 项因已知 Windows ids.exe 安全拦截主动跳过。构建 exit 0、2 条警告：[构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-182738-build-49344/raw.log)。
- 官方更新 succeeded / healthy=true / validated=true：[更新日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T103008Z-system-update/raw.log)。Operation ID：3444fd45-fbdb-4cf8-9a00-01be6605680a。
- 同一 Device ID 4553502d49524953010030eda0f45ede，Boot：7591637753890153799 → 7618025230291254864（Vibe）→ 1304414306557918869。当前 ELF / 设备 SHA-256：9031bd50c72316201214277a919d87aa524b9c07a88c9dc27c0f2d7bc6826e4f。
- [更新后实时状态](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T103229Z-device-status/raw.log)：edge_agent / normal / idle，stale=false，crash_count=0。bootloader、分区布局和用户数据保留。
- 实机两轮受控状态检查均为 access=automatic / waiting_game，无 code；已释放测试连接。更新后没有启动游戏，未代替用户进行游戏操作。
- 网页脚本语法检查通过；本轮不运行网页 UI 自动化。现有原型检查已更新预期，不把旧审批测试通过记录当作新流程验收。

手验重点：运行原 v2 后进入手柄能否直接就绪、组合按键、多指摇杆、断线归零及自动恢复、退出不停止游戏。当前设备未运行游戏时等待属于正常；固件 healthy 和 waiting_game 检查不代表完整操控链路已经验收。

## 2026-10-13 18:18 握手重试修复

用户反馈点击「请求控制」始终返回「控制请求已结束或被接管」。已在更新前用真实设备的 WebSocket 首个 poll 复现 released；当时没有运行游戏，正确状态应为 waiting_game。

根因：固定 ESP-IDF 6.2 的 httpd_uri.c 在 WebSocket 升级时跳过普通 URI handler，原先写在 handler 的 HTTP_GET 分支里的 gamepad_connect 从未执行。已将请求登记移入 ws_pre_handshake_cb，在返回 101 前完成；占用时返回 HTTP 409 并拒绝升级，普通 HTTP GET 不登记请求。仅修改固件握手入口，Android 与 Lua v2 内容不变，无需重新安装 App 或重新获取已保存的 v2。

- 现有固件测试：10 项通过，1 项因前次已确认的 Windows ids.exe 安全拦截主动跳过；未绕过防护。首轮环境默认 GBK 读取 UTF-8 源码报错，设置 PYTHONUTF8=1 后测试正常。
- 增量构建成功（exit 0，2 条警告）：[构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-181444-build-11132/raw.log)。
- 官方更新 succeeded、healthy=true、validated=true：[更新日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T101612Z-system-update/raw.log)。Operation ID：c8b868c7-3f6f-4209-a4ed-dfaf19ca2841。
- 同一 Device ID：4553502d49524953010030eda0f45ede；Boot 依次为 18230607446407449864 → 10808003046549888424（Vibe 写入）→ 7591637753890153799。bootloader 和分区布局 hash 未变，保留用户 NAND / NVS。
- 当前固件 ELF / 设备 SHA-256：3b32baa8fc61eebc5ffae2abfef5ffbc875f8939574253203909f872a6713fcd；bin SHA-256：9a31780b97a5506113ed007d2156dbe6e37dc61b97859b8aafb3dc51a1dd5252。下方首版 SHA 为历史记录，同路径构建产物现已更新。
- [更新后实时状态](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T101815Z-device-status/raw.log)：edge_agent、normal / idle、stale=false、crash_count=0，固件身份与本次构建一致。
- 实机受控协议复查 3 轮：每轮建立新连接，首个 poll 均返回 waiting_game 和六位编号，再次 poll 保留该编号，release 返回 released；随后可立即再次请求，新编号分别为 454655、388453、470177。只查询和释放控制请求，没有发送游戏输入或自动批准。

更新重启后游戏没有自动运行。请重新运行已获取的 v2，再打开手机手柄请求控制，在设备上核对编号并点 ALLOW。此次验证覆盖请求建立与重试，不代表设备触屏批准、游戏操控或延迟已验收。

## 如何使用

1. 手机和 Mosaico 接入同一可信局域网，在 App 设备页确认已连接；支持原有自动发现、重新扫描与手动地址。
2. 从应用中心重新获取「坦克大战」「飞机大战」或「午夜赛车」的 v2，按原作品流程确认部署并运行。此次安装不自动替换旧作品，也没有预先启动游戏。
3. 打开「应用 → 我的创作 → 让手机，成为手柄」，进入横屏通用手柄并请求连接。
4. 核对手机与 Mosaico 游戏画面的六位编号，在 **Mosaico 触屏点击 ALLOW**。控制就绪后操作；每次重连或新游戏运行需要重新批准。

原 v1 本地副本和设备包保留；旧版不会因手机更新自动支持手柄。需要运行新获取的 v2，而不是历史作品中的 v1。

| 游戏 | 控制方式 |
| --- | --- |
| 坦克大战 v2 | 方向键 / LS 移动转向，A / R1 开火，可同时移动与射击 |
| 飞机大战 v2 | 方向键 / LS 移动，自动射击；LS 保留推动幅度 |
| 午夜赛车 v2 | 左右方向键每次按下换一条车道；LS 超过阈值换道，回中后可再次同向换道 |

三款游戏均以 A 开始 / 重试、Start 暂停 / 继续；这是游戏 Lua 的定义，不是手机强制映射。原触屏、PAUSE / PLAY / EXIT 保留。

## 已实现范围与边界

- 独立横屏手柄：双摇杆、十字键、ABXY、L1/R1/L2/R2、L3/R3、Select/Start。所有标准键保持可见，游戏自行决定用途；没有按游戏名称切换专用布局。
- 遵循确认稿和 UI/UX 技能的安全区、48dp 触控目标、按下反馈与无障碍语义要求；窗口过窄时提示调整，不裁切完整手柄。
- 独立局域网 WebSocket 输入通道，按钮变化即时发送、状态约 30Hz，包含运行实例、控制令牌、递增序号与顺序按键边沿；不经过模型或 Claw 聊天。
- 手柄菜单先释放输入，不发 Start、不停止游戏；退出恢复原方向策略和应用列表位置。后台、断线等释放会话；设备超时兜底归零，三款 v2 自行在失联后暂停。
- 保留 Claw 原有应用、Works、Hub 与通知栏，不向系统界面注入手柄操作，不改变原作品部署业务。
- 仅适配上述三款 Lua v2。其他旧 Lua、Dino / Flappy / Bricks 尚未适配；「通用」指输入协议统一，不代表任意旧游戏零修改兼容。
- 本轮不引入 gpSP、gnuboy、Doom 或 Quake 1，不下载或分发 ROM / WAD / PAK。
- 仅面向可信 LAN：当前通道没有 TLS，六位编号用于现场核对而非密码。批准界面由可信 Lua 游戏实现，现有 Claw 文件与执行接口仍需信任，不构成完整安全沙箱。

若曾手动设置 Claw 的非空 enabled_lua_modules 白名单，需要包含 gamepad；默认空列表启用全部模块。本次不覆盖用户已有配置。

## 验证与安装结果

| 项目 | 结果及范围 |
| --- | --- |
| Android 现有单元测试 | 175 项通过，0 失败、0 错误 |
| Android 构建与安装 | testDebugUnitTest + assembleDebug 成功；ADB install -r 成功，未卸载或清空数据 |
| 固件现有测试 | 11 项中 10 项通过；1 项运行本机测试程序时被 Windows 安全策略阻止，未绕过防护 |
| 固件构建 | ESP-IDF 固定版本构建成功，exit 0；低噪声构建记录有 2 条警告 |
| Mosaico 系统更新 | 官方更新流程 succeeded，healthy=true、validated=true；实际固件身份与构建产物一致 |
| 更新后状态 | normal / idle，stale=false，crash_count=0 |
| 真实操控 | 尚未实机验证游戏画面、批准按钮、网络按键链路、多指手感及端到端延迟，交由用户手验 |

Lua 现有测试使用全零 gamepad mock，覆盖源码执行及既有触屏/帧/清理路径，不能作为移动加射击、短按或断线处理已实测通过的证据。固件启动健康也不等于游戏交互验收通过。

被安全策略阻止的测试为 tools/tests/test_phone_works.py::test_only_immutable_phone_package_ids_are_exposed：编译成功，运行 ids.exe 出现 WinError 5，清理时出现 WinError 225。

### 产物与证据

- [Android APK](../../projects/mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk)
- [固件更新包](../../projects/esp-mosaico-claw/build-mosaicobot/edge_agent-system-update.irisfw)
- [固件构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-175819-build-47216/raw.log)
- [更新原始日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T100204Z-system-update/raw.log)
- [更新后实时设备状态](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T100406Z-device-status/raw.log)

安装手机：PZ261A，ADB serial ZYPZ261A01M66A000UC。

- APK SHA-256：1f88693300effb06e9000432ad1243ed0bfb3d043f65960f17926b09913e7dfa
- 固件 ELF / 设备身份 SHA-256：e2723a38febbde8962b1198de357d2b5021abb654a675a38300a12bf219dbf4a
- edge_agent.bin SHA-256：57c3c65a99891483c0d09ec3e8462b589a94c05e6d676d25f2834112a6d588d6
- IDF commit：7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe，target esp32s31

Mosaico 更新追踪：

- Device ID：4553502d49524953010030eda0f45ede
- Operation ID：bf73c457-0e4c-453f-a75e-c38c8fe1ab2a
- 更新前 Boot：11854559113351912216
- Vibe 写入阶段 Boot：3938167629201765209
- 更新后 Boot：18230607446407449864

官方更新 manifest 包含 partition_table / ota_0 / ui_apps / system，不包含 Vibe、NVS 或用户 NAND；bootloader、partition table、ui_apps 的 hash 更新前后不变。未更换为 Gamer 固件，未改分区布局。

运维备注：bundle 方式的更新操作记录归属 Vibe 默认 projects/snake Gateway 名称空间，不代表刷入 snake；实际传入 edge_agent 更新包，最终设备 project_name 为 edge_agent 且固件 SHA 匹配。查询此次 operation 时需使用对应名称空间，查询不到不应重复刷机。

## 用户手动验收清单

- [ ] 获取 v2 后可在 Mosaico 运行，原 v1 和其他作品仍保留。
- [ ] 横屏控件完整、比例合适；两种横向握持、返回原页面正常。
- [ ] 两端六位编号一致；ALLOW 前不可控制；DENY / 超时不会启用输入。
- [ ] 坦克可边移动边开火，松手停止对应操作。
- [ ] 飞机支持斜向及按幅度移动，按钮和摇杆可多指同时使用。
- [ ] 赛车快速短按不漏换道；持续按住不会按帧连续跳道；LS 回中后可再次换道。
- [ ] Start 可暂停/继续三款游戏；打开手机手柄菜单只归零输入，不冒充游戏暂停。
- [ ] 断网、锁屏、切后台后不残留移动/开火；重新连接须批准，不重放旧操作。
- [ ] 退出手机手柄不删除、重新部署或强制停止游戏；三款游戏可按自身失联逻辑暂停，并可触屏恢复/退出。
