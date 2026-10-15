# MosaicoBot · Coding Agent 交接文档

更新时间：2026-10-14（Asia/Shanghai）。用于接续当前开发窗口，不是新的功能授权。

工作区：D:/Android/Projects/my_workspace。本文路径：docs/mosaicobot/AGENT_HANDOFF.md。

## 0. 两分钟接手摘要

- 产品：MosaicoBot 让手机长出可编程的身体。用户在手机表达意图，手机 Agent 生成 Lua 应用，保存版本、确认部署，Mosaico 独立运行并反馈；手机不是游戏运行/投屏主体。
- 当前路径：原生 Android App + esp-mosaico-claw 的 Works/Lua 运行时；esp-mosaico-vibe 提供保留 Vibe 固件、官方构建/更新/诊断工具。不是把手机 App 包装为网页，也不是每生成一个 Lua 就重新刷固件。
- 已有：创作/应用/设备三页、真实模型生成与有界续写修复、局域网自动连接、作品部署复用和启停、本地应用中心、设备 Claw 独立对话、设备系统视觉重做、横屏通用手机手柄。
- 最新完成：修复“极速跑酷切到手机手柄后反复暂停”。Android 与固件已更新，跑酷修复版 v3 已保存并部署运行，原 v2 和其他作品保留。
- 最新有限验证：Android 199 项单测通过；固件 11 项通过、1 项因既有 Windows 安全策略排除；官方固件更新 healthy/validated；跑酷启动画面正常，手机手柄就绪并持续收到 ACK。没有完整跑酷玩法、人工弱网或长时手感验收，也没有本轮真实模型生成测试。
- 下一步：等待/接收用户对跑酷 v3 的手验反馈，再针对问题改动。不要重复已经完成的安装和刷写，不要擅自开始云端或模拟器引擎开发。
- 用户偏好：先按主设计稿对齐风格与比例，不靠调大字体/全局缩放补布局；保留原应用和下拉栏，业务尽量少改。近期多次要求只做必要构建/单测，剩下由用户手验，避免无目的全量 UI 自动化和重复模型调用。

设备在线状态、IP、Boot ID、构建产物可能在交接后变化。下文是上次验证快照，不是新会话的实时检查结果。

## 1. 产品需求与已确认边界

### 1.1 创作与运行

目标体验是“说出意图 → AI 创造硬件行为 → 部署运行 → 反馈 → 继续修改”。当前实现是可信局域网开发版：每个版本运行前明确确认，尚未实现无条件自动执行。

- 手机可以未连接 Mosaico 时生成并保存作品；部署和运行才要求连接设备。
- 一个作品可有多个不可变版本；保留旧作品，不用升级/重装顺便清理用户数据。
- 已部署的同版本先读回验证源码/清单，正确则直接复用，不重复上传或发布。
- 切换作品时先查状态，必要时停止正在运行的其他手机作品、等待释放屏幕，再启动目标；不自动停止系统/预装应用。
- 上传成功 ≠ 发布成功 ≠ running ≠ 首帧/交互验收。不能用模拟画面或 HTTP 成功冒充真正运行。
- 手机生成 Agent 与设备 Claw 是两套角色/配置，不能将手机 Key 交给设备 Claw。

### 1.2 手机页面和设计基线

主设计稿：[index.html](index.html)。它是本地模拟评审稿，不连接真实模型、设备或手机数据。

- 手机普通页面为竖屏：创作 / 应用 / 设备；只有游戏手柄模式强制横屏。
- 创作页：轻量会话、作品卡、修改建议、输入框；源码和摘要进入详情，不占满中间可用区域。
- 应用页：我的创作 / 应用中心 / 设备应用。主设计稿的比例、留白、卡片和固定操作区是对齐基准。
- 设备页：连接、能力概览、运行授权说明、设备 Claw 对话、折叠的手机 Agent 配置。能力展示不等于生成 API 已开放。
- 用户明确反对通过扩大字体、修改系统 density/font scale 或缩放整个 App 来“对齐”；要修布局结构和空间比例。
- 上传弹窗已重做：暖白紧凑面板，固定标题、可滚动字段、固定隐私确认与操作区，源码审查入口、分类及字符限制；不要恢复早期粗糙弹窗。
- 大的新交互先更新主设计稿/交互文档并确认，再开发；已确认范围内的 bug 修复可直接实现。用户后来明确允许设备视觉稿更新后直接编码构建刷机，该阶段已经完成。

用户曾指定 UI/UX 技能；其实际存在路径为 C:/Users/admin/.cursor/skills/ui-ux-pro-max/SKILL.md。早期消息中 D:/Android/Projects/my_workspace/c:/Users/... 是错误拼接路径。需要重新做相关 UI 时先按当前 Agent 的技能规则读取；本次交接仅记录位置。

### 1.3 Mosaico 原应用和设备外观

- 保留 Claw 原有 11 个主屏入口及 Works 作品。Camera / CLAW / Works，Settings / IMU / Album / Music / Bricks / Weather，Interaction / Partner 不删除。
- Works 内 Dino / Flappy Bird / Album 保留；Works Album 与系统 Album 是不同应用，不能合并当重复项。
- 系统视觉已按 Graphite Instrument / 石墨仪表落地：近黑底、暖白文字、线性图标、克制信号橙。
- 重做范围：首页、两页应用、Works、下拉控制栏、通知列表、AOD、充电、模块插入。原应用内部业务、既有导航和启停尽量不改。
- Wi-Fi 状态图标已由 40×40 缩到 24×24，原槽位内偏移 (8,8) 居中；其余整体视觉用户反馈基本可以。
- Join/Bluetooth/Low power/Ringtone 等原本未实现的控制仍是 RESERVED/Not Supported；音乐卡只打开 Music。通知外观不是新增完整通知后台。
- 手机“设备应用”目录主要来自源码映射，不是设备实时安装清单；除 Claw 对话外，不声称所有原应用已能远程打开/停止。

详见 [设备视觉说明](hardware-redesign/README.md) 和 [设备视觉交付](hardware-redesign/delivery.md)。

### 1.4 应用中心：本地模拟已落地

目前没有云服务器。目标是未来共创共享；当前只有本机目录、上传快照、浏览、搜索/分类、获取和移除自己的上传。

预置六款真实 Lua：坦克大战、飞机大战、午夜赛车、口袋像素宠、光点画板、一刻专注。

- 点击获取才把独立副本加入“我的创作”，不自动部署/运行；同来源同版本去重。
- 上传保存所选版本的源码快照，后续编辑不覆写上传条目；移除中心条目不删除创作副本或设备应用。
- 只在本机可见，不虚构其他用户、评分、下载量、公开云端上传或跨手机同步。
- 上传需名称、介绍、分类、源码审查入口及权利/隐私确认。名称还受设备 48 UTF-8 字节限制。
- 用户明确要求开发阶段直接改应用中心源码，不要为这些调整不断升级中心预置版本；已有用户副本不自动覆写。

## 2. 通用手机手柄：当前确定方案

参考方向来自用户提供的固件示例：
https://mosaico-ideas.espressif.com/firmware/51f0f5d0-4cb2-4adc-a020-5f1903d391b1

这是产品体验参考，不表示已导入该固件/引擎。当前以手机替代实体手柄，Mosaico 显示并运行游戏。

- 入口：“应用 → 我的创作 → 让手机，成为手柄”。进入横屏传统手柄布局。
- 固定通用控件：双摇杆、十字键、ABXY、L1/R1/L2/R2、L3/R3、Select/Start。不能按某个游戏名称切换专用遥控器界面。
- 游戏解释标准输入；不是手机推断任意游戏热区，也不是任意旧游戏零改动兼容。
- 已适配三款预置游戏；新生成游戏默认要求触屏/手柄双操作；用户旧游戏可以修改 Lua 适配。
- 进入前台自动检查设备/游戏/首次输入回执；可用即控制，不需要“请求控制”、配对码、设备 ALLOW/DENY。
- 用户明确要求移除上述请求批准逻辑，不要因旧文档再加回去。
- 仍保留单控制者、占用等待、过期释放、非法包拒绝、退出/后台清零；不抢占另一部手机。
- 手柄菜单不是 Start，不自动发暂停键；退出手柄不部署、停止、重开或恢复游戏。
- 输入走局域网独立 WebSocket /ws/gamepad，不经过模型或 Claw 聊天。
- 初始无手机照样触屏玩；触屏始终保留开始、核心操作、暂停/继续、重试、EXIT。

后续愿景但尚未开发：GBA gpSP、GB/GBC gnuboy、Doom、Quake 1。需要单独确认范围并评估资源、性能、许可证与输入适配；不要下载/分发 ROM/WAD/PAK，不要把引擎接入写成已完成。

### 2.1 已修过的连接问题，避免重复踩坑

1. 最初请求一直结束：固定 IDF 在 WebSocket 升级时跳过普通 HTTP_GET URI handler，必须在 ws_pre_handshake_cb 登记连接；该握手入口已修复。
2. 用户后来取消物理批准：新连接自动分配 token/run，第一帧全零 state 得到 ACK 后启用控件；approve 只留旧 Lua 的无操作兼容 stub。
3. 手机“正在连接/手柄”反复切换：此前 350ms ACK 超时与积压策略太激进。已使用 2.5s 失联判断、最多 8 个周期状态待确认、16 帧为按键边沿保留空间。
4. 热点延迟：手柄 socket 开 TCP_NODELAY；gamepad runtime 存在期间暂时关闭 Wi-Fi modem sleep，退出恢复原模式，不改 NVS 电源设置。

### 2.2 最新问题：极速跑酷反复暂停（已修改交付，待用户手验）

用户现象：Mosaico 单机触屏玩正常，一切手机手柄就反复暂停。不是简单判断“模型不行”或“手机根本没连上”。已确认固件与游戏的连接语义不匹配：

- 旧固件在超过 350ms 无新输入时清零，并把 Lua connected 变 false；手机却到 2.5s 才判失联，可能一直显示手柄就绪。
- 跑酷 v2 按 connected 从 true 变 false 进入正式 paused，造成短暂抖动也暂停。
- 跑酷还用 held 重建边沿；安全清零后恢复同一长按有机会被误认作再次按下。
- 原 v2 与设备源码已比对一致。原手机日志在同一会话持续 ACK；此前没有逐次 stale 日志，不能声称每次暂停原因都已逐帧实测。

本轮修改：

| 状态/动作 | 当前语义 |
| --- | --- |
| 首个有效 state 前 | connected=false，不应阻止触屏游戏 |
| 新鲜输入 | connected=true，input_active=true |
| 超过 350ms 无新有效输入 | 清 held/axes/edges，input_active=false；保留 connected/token/seq/物理按键基线 |
| 短过期时游戏 | 临时冻结模拟，显示 PAD SIGNAL WEAK；继续绘制、轮询、更新时间基准，触屏可接管，不设正式 paused |
| 输入恢复 | 仅解除临时冻结，不能解除用户手动暂停；持续按住 Start 不合成新 pressed |
| 超过 2.5s / release / socket close | 真正撤销会话，connected=false，游戏只正式暂停一次 |
| 暂停键 | 用 pressed/events，不从 held 推导；同帧 touch/Start 合并后仅切换一次 |

计时器每 50ms 检查，实际触发还受调度影响。旧固件兼容判断用 input.input_active == false，不能用 not input.input_active 将缺失字段误当过期。

修复涉及：生产固件状态机及诊断日志、手机 ACK/Start 诊断日志、Agent 生成规则/结构检查、预置游戏共用循环、用户跑酷 v3。没改跑酷场景、碰撞、积分等核心玩法。

## 3. Agent 生成能力与安全边界

当前已支持屏幕/触摸、真实时钟、受限 HTTPS GET 数据展示、手机手柄。旧文档“仅屏幕和触摸、不能联网”已经过时。

### 3.1 截断/格式恢复已实现

- 输出默认是一个完整 RAW LUA 应用包，不再要求模型把整段 Lua 转义为唯一 JSON 字符串；兼容旧 JSON/完整代码围栏。
- 格式：MOSAICOBOT_APP_V1 标记、title、summary、完整 Lua、MOSAICOBOT_END 标记，具体读取 agent-contract.txt。
- 每请求输出预算 16384 token；非流式，读取 300s、单次总时限 360s。
- 整个生成最多 8 次请求、12 分钟；每候选最多续写 3 次，完整重写修复最多 2 次，可取消。
- 缺后缀才精确续写；完整包语法/能力/交互不合格应完整修复；完整替代包替换旧候选，不能拼成两个程序。
- 唯一完整应用通过语法/能力检查才保存；失败保留需求和旧版本，不保存残缺游戏，不无限重试，也不保证每次生成成功。

### 3.2 默认双操作生成

Lua 源码首行声明 interaction: game-dual / game-touch / app。默认游戏必须 game-dual；仅用户明确不要手柄时才 game-touch，非游戏 app。

GeneratedGamePolicy 仅在生成/编辑返回时做结构检查：触摸、EXIT、pad 生命周期、input.connected/input.input_active、真实输入和 Start 引用。忽略注释/字符串中的伪调用。缺失则交回既有修复循环；旧作品读取、导入、部署不套新门槛。

结构检查不能证明热区、代码可达性、实际玩法或模型分类正确；不要直接在主机执行任意模型 Lua 来“验证安全”。新生成游戏仍要真机手验。

### 3.3 网络与权限

- 设备自己 Wi-Fi 联网，使用已有 capability.call("http_request", ...)；不是手机代理，也不虚构 http.get。
- 当前限定 HTTPS GET、8s、16KiB body、约 17KiB output，不附鉴权头/模型 Key；具体参数顺序受保守 lint 约束。
- 部署前核验模块、HTTP capability、设备域名 allowlist；没有授权的域名不可擅自放开。
- HTTP 是同步调用，会暂时阻塞帧/触摸。先提交加载画面；天气低频刷新、失败退避，不伪造实时数据。
- 相机、麦克风、音频、文件/执行器、网络写入和鉴权密钥等尚不在手机自由生成的开放范围；硬件存在不等于 Agent API 已开放。
- 手机 lint 不是安全沙箱。文件/执行/手柄通道仅用于可信 LAN，无 TLS/完整安全配对；token/run 用于隔离旧会话，不是身份鉴权。不要暴露公网。

## 4. 项目和代码导航

相对工作区根目录，先按当前任务读取相应文件，不需要全量扫 third-party。

| 位置 | 责任/入口 |
| --- | --- |
| projects/mosaicobot-android | Kotlin / Jetpack Compose 原生 App，包名 dev.mosaicobot；属于工作区根 Git 仓库 |
| projects/esp-mosaico-claw | 设备 Claw 固件、Works/Lua、系统 UI、HTTP 和手柄；独立 Git 仓库 |
| projects/esp-mosaico-vibe | 官方 mosaico.py、保留 Vibe/Recovery、Iris 运维工具及子模块；独立 Git 仓库 |
| docs/mosaicobot/index.html | 主交互稿；配套 styles.css/app.js、app-center.*、game-mode.*、device-apps.js |
| docs/mosaicobot/interaction.md | 产品与页面交互；含阶段性旧说明，冲突时看本交接和最新交付 |
| docs/mosaicobot/game-mode.md | 通用手柄与生成双操作协议 |
| docs/mosaicobot/game-mode-delivery.md | 最新修复、设备身份、构建/操作日志及历史交付；优先读顶部当前修复 |
| docs/mosaicobot/hardware-redesign | 设备视觉方案和交付；已整合主稿，不是另起一套新产品 |
| docs/mosaicobot/development.md / validation | 早期闭环、USB DMA 修复及验证历史，不代表所有字段都是当前状态 |

Android 文件均在 app/src/main/java/dev/mosaicobot/ 下：

| 模块 | 文件 |
| --- | --- |
| 入口、状态编排 | MainActivity.kt、BotViewModel.kt、Models.kt |
| 创作与模型恢复 | CreationScreen.kt、AgentClient.kt、ArtifactEnvelope.kt、ArtifactValidator.kt |
| 生成策略 | GeneratedGamePolicy.kt、LuaNetworkPolicy.kt；实际 prompt 在 app/src/main/assets/agent-contract.txt |
| 设备发现与请求 | LanDiscovery.kt、LanSubnet.kt、DeviceConnections.kt、DeviceClient.kt、Network.kt |
| 发布/启动/复用 | WorkLaunch.kt、WorkRuntime.kt、LibraryWorkStatus.kt |
| 设备与独立 Claw | DeviceScreen.kt、ClawChat.kt；/api/webim/status、/ws/webim、/api/webim/send |
| 应用中心 | ApplicationsScreen.kt、AppCenter.kt、AppCenterScreen.kt、CenterUploadDialog.kt、CenterArtwork.kt |
| 手机手柄 | GamepadScreen.kt、GamepadSession.kt |
| 本地数据与凭据 | LocalStore.kt：works.json、app-center.json；Key 用 Android Keystore AES-GCM |
| 预置应用 | app/src/main/assets/app-center/；三款游戏共用 game-common.lua |
| 最新测试 | app/src/test/java/dev/mosaicobot/GameInputRecoveryTest.kt、GeneratedGamePolicyTest.kt |

Claw 关键路径：

- components/lua_module_gamepad/src/lua_module_gamepad.c：最新 input_active/edge_baseline/超时修改；同组件 README 是协议依据。
- components/http_server/http_server_gamepad_api.c：WebSocket 握手、输入和 socket 生命周期。
- components/http_server/http_server_works_api.c 与 WORKS_API.md：作品发布、状态、启停。
- components/mosaic_ui/common/works_runtime.c：设备 Works 生命周期，不在 HTTP 线程直接绘图。
- components/http_server/http_server_files_api.c、http_server_status_api.c、http_server_lua_modules_api.c：文件、设备状态、模块。
- components/mosaic_ui/hub/、apps/works/、common/graphite_assets.py 等：设备系统界面与资源生成；改源生成器，不仅改生成产物。
- tools/cmake/mosaicobot_profile.cmake、sdkconfig.mosaicobot.defaults、partitions_mosaicobot.csv：保留 Vibe 专用构建配置。
- tools/tests/test_gamepad_input.py：用模拟时钟/RTOS/Lua API 编译执行真实生产 C 状态机。

## 5. 最新已交付快照与保留数据

这些是 2026-10-14 上一轮开发交付时的信息；本次整理文档没有再次操作手机或设备。

- 手机：PZ261A；ADB serial ZYPZ261A01M66A000UC；dev.mosaicobot 已 install -r，未卸载/清数据。
- Mosaico Device ID：4553502d49524953010030eda0f45ede；MAC：30:ed:a0:f4:5e:de。
- 最近 LAN 地址：http://192.168.137.246（PC 热点，不能硬编码为长期地址）；最近 USB COM19 / usb:location=1-1:x.0。
- 固件：normal / edge_agent 0.4.0，stale=false，crash_count=0。
- Boot：6583509893975638076 → 10248673715909024318（Vibe）→ 1546457411718506444。
- 固件 ELF/设备 SHA-256：6e79443ed58965c500d927c4ff0b51012f8d4b3751ae874222b927ed52ba9b1b。
- 最新 update operation：92a83da5-27ae-4e82-b441-96caca6cf15d，succeeded / healthy=true / validated=true。
- APK SHA-256：c3265fb2fe6e13445ecc93ea82a96105b9407815ef97531cbfd77796a115d116。

### 极速跑酷作品

- 同作品 ID：a350510230114d80；修复版 v3，原 v2 不删；手机共有 6 个版本条目（v3、v2、其他 4 个）。
- v3 包：mb_a350510230114d80_v3_62719551。
- v3 Lua SHA-256：62719551f26de103c1b4a23a270ed1b61f897248da4f11b936cde468e629c8be。
- 原 v2 包：mb_a350510230114d80_v2_4a587e1d。
- 本机修复材料：projects/mosaicobot-android/.codex-runs/runner-input-fix/，含 original-v2.lua、repaired-v3.lua、repaired-work.json、VerifyRunner.java 等。
- v3 不只是 APK 资源：已写入手机私有 works.json，后经正常 App 确认流程部署并启动到设备。重新构建 APK 不会自动重建这个用户作品。
- 修改手机数据前备份为 files/works-before-input-fix.json，保留已有版本并原子更新。交接后不要把旧 works-after.json 再覆盖回手机，会丢掉后来部署状态或用户新作品。优先使用 App 正常操作；若确需迁移，先重新读当前数据并备份/合并。
- 交付时设备显示 SUPER PIXEL RUN / PLAY，手机手柄“控制已就绪”，按 A 可开始。这不表示用户此刻仍停在该界面。

### 证据及验证范围

- [最新详细交付](game-mode-delivery.md)。Android 199 项单测 0 失败/错误/跳过。
- 固件 11 passed、1 deselected、2 subtests passed；排除项是 test_phone_works.py::test_only_immutable_phone_package_ids_are_exposed，编译的 ids.exe 曾被 Windows 安全策略阻止（WinError 5/225），没有绕过。
- 新测试覆盖 350ms/2.5s 边界、重复序号、Start 长按跨过期、释放重按、真实断线、触屏接管、同帧暂停合并。
- [正确构建日志](../../projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261014-114039-build-46792/raw.log)：OK、exit 0、2 条警告。
- [官方更新日志](../../projects/esp-mosaico-vibe/.codex-runs/mosaico/20261014T034239Z-system-update/raw.log)。bootloader/完整分区表/ui_apps 哈希不变；包未包含用户 NAND/NVS/Vibe 固件。
- [设备启动画面](../../projects/esp-mosaico-claw/.codex-runs/gamepad-input/runner-v3-ready.png)已直接查看，不是网页截图。
- [手机 ACK 日志](../../projects/esp-mosaico-claw/.codex-runs/gamepad-input/android-runner-v3.log)：11:46:27.844 至 11:47:08.081，同 epoch=3，确认 1200 帧，无区间内自动断开；仅是待开始状态连接检查。
- 尚未验证完整跑酷操控、主动注入弱网、长时间稳定性或本轮真实模型生成。早前坦克约 154 秒的操控证据不能冒充跑酷本轮验收。

## 6. 环境、凭据与正确构建方式

### 6.1 先读约束，不清理工作树

先读工作区 CLAUDE.md、rules/global.md，以及对应项目规则/AGENTS.md。Vibe 的项目指南和技能对所有本设备操作适用。必要时读取 third-party 自己的 AGENTS.md。

工作树存在跨多轮的修改，根仓库与两个嵌套仓库分开管理；不要假设未提交/未跟踪文件是垃圾，不做 git reset --hard、checkout --、全盘清理或批量删除 docs。用户早期清理无用稿件的要求不是每次接手删历史证据的许可。

本地编辑使用 apply_patch。Windows 长任务后台运行用隐藏窗口，完整日志落 .codex-runs，不靠长时间阻塞等待。不要关闭安全软件。

### 6.2 模型配置（不含密钥）

- Base URL：https://models-proxy.stepfun-inc.com/v1。
- 默认模型：step-5-preview:stepfun；POST /chat/completions；该模型用 reasoning_effort=low。
- 用户的 Key 已安全配置到手机，不写本文、不打印配置、不复制进 Lua/APK/Git。原先 PC 默认 gpt-6-astra 对用户 Key 返回 403，不能擅自改回。
- 手机接 PC 热点后曾验证直连模型成功；旧 USB CONNECT 代理/ADB reverse 已移除，不能把它们当当前必需架构。
- 当前为内网代理服务，不能承诺移动网络/任意路由器都能访问。更换设备时通过 App 的“设备 → 模型与接口设置”由用户配置，不借用其他 PC 账户凭据。

### 6.3 Android 构建 / 测试 / 安装

在 projects/mosaicobot-android 执行：

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
$env:ANDROID_HOME='D:\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
& 'D:\Android\Sdk\platform-tools\adb.exe' devices -l
# 仅在用户要求安装/交付且核实目标后：
& 'D:\Android\Sdk\platform-tools\adb.exe' -s ZYPZ261A01M66A000UC install -r app/build/outputs/apk/debug/app-debug.apk
~~~

Android 11/API 30+，SDK 36；构建产物 app/build/outputs/apk/debug/app-debug.apk。不要卸载清数据解决问题。

### 6.4 Mosaico 固件：必须 build-mosaicobot，不是默认 build

Pinned IDF：C:/esp/esp-idf-7b9cc1ac，提交必须为 7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe，target esp32s31。

该 SDK 有既有 USB restart/DMA 专项修复背景；不要为追求 clean checkout 删除本地补丁或切换最新 IDF。先核对项目记录和实际差异。旧 GDMA 实验已排除，不能从历史实验文件重新应用。

IDF Python：C:/Users/admin/.espressif/python_env/idf6.2_py3.14_env/Scripts/python.exe。系统 python 可能是无效 Store alias；通用 py -3.14 可用，但不替代已验证的 IDF 环境。

必须先按 Vibe 的 AGENTS.md 读取 .agents/skills/idf-low-noise-build/SKILL.md，首次在新环境跑 doctor 并确认 pinned commit/目标。正确增量构建（cwd 为 projects/esp-mosaico-vibe）：

~~~powershell
$py='C:\Users\admin\.espressif\python_env\idf6.2_py3.14_env\Scripts\python.exe'
$runner='submodule/esp-mosaico-utils/mosaico-tools/skills/idf-low-noise-build/scripts/idf_low_noise_build.py'
git -C C:/esp/esp-idf-7b9cc1ac rev-parse HEAD
& $py $runner --project ../esp-mosaico-claw --idf-path C:/esp/esp-idf-7b9cc1ac doctor
$env:GSPC_EXECUTABLE='C:\Users\admin\.cache\esp-mosaico\gspc\0.6.1\gspc.exe'
& $py $runner --project ../esp-mosaico-claw --idf-path C:/esp/esp-idf-7b9cc1ac build --build-dir build-mosaicobot -D ESP_MOSAICO_BUILD_PROFILE=tools/cmake/mosaicobot_profile.cmake
~~~

产物在 projects/esp-mosaico-claw/build-mosaicobot/：edge_agent.bin、edge_agent.elf、edge_agent-system-update.irisfw。

本轮曾误构建默认 build/，其 Claw 分区契约不兼容，被工具拦截，未写入。默认 build/ 即使时间较新也不是可用交付产物。不要改分区绕检查，不要为普通改动 fullclean。Python 3.14 下 GSPC 下载器曾出现 ZipInfo has no read，复用匹配 0.6.1 缓存即可；构建 Vibe 本体需按其独立版本要求，不套用应用 GSPC。

固件测试可复用本轮已装依赖的隔离环境（cwd projects/esp-mosaico-claw）：

~~~powershell
$env:PYTHONUTF8='1'
& '.codex-runs/gamepad-input/test-env/Scripts/python.exe' -m pytest tools/tests components/mosaic_ui/common/test_scene_assets.py -q --tb=short -k 'not test_only_immutable_phone_package_ids_are_exposed'
~~~

保留排除说明，不把它报成全部通过；若测试环境不存在，按测试依赖重建，不绕过操作系统防护。

### 6.5 官方设备更新：只用 mosaico.py

先读 projects/esp-mosaico-vibe/AGENTS.md、.agents/skills/mosaico-device-operations/SKILL.md、docs/mosaico-cli_CN.md、docs/project-gateway_CN.md、docs/device-modes.md。

“刷机”不等于 ROM。当前设备已有保留 Vibe，正常更新不执行 recover/全擦，不使用 idf.py flash、esptool 写入或直接调用底层 ESP-Iris 写设备。

先 status --all 和 list --details，核对归属、选定设备及 live device-status。不要终止其他 Gateway、直接抢串口或换另一块板；缓存发现/IP/端口不是实时身份。写入前保留日志和有效 core dump。

已构建独立目录时用 bundle。当前 CLI 的 --bundle 与 --project/--skip-build 互斥；不要照搬失败命令。以下是显式目标的命令模板（仅得到相应交付授权后运行，cwd projects/esp-mosaico-vibe）：

~~~powershell
$py='C:\Users\admin\.espressif\python_env\idf6.2_py3.14_env\Scripts\python.exe'
& $py mosaico.py iris status --all --json
& $py mosaico.py iris list --details --json
# 先按发现结果协调归属、claim、device-status；同机确认后才更新：
& $py mosaico.py iris system-update --device-id 4553502d49524953010030eda0f45ede --bundle ../esp-mosaico-claw/build-mosaicobot/edge_agent-system-update.irisfw --timeout 300
~~~

- 当前工具 bundle 模式使用工作区默认 projects/snake Gateway 名称空间，即使从 Claw cwd 启动也曾如此；这不代表安装 snake。看实际 bundle、设备 project_name 和 ELF SHA。
- 本轮 operation 属于 projects/snake，可用 iris operation-status <ID> --project projects/snake --json 查；后续以工具实际输出为准，不为了统一名称随意改默认项目。
- 默认 iris app-update --skip-build 会读默认 build，不能用它代替独立 build-mosaicobot 的 bundle 更新。
- 超时/断开先查原 operation，不重复发写入、不改选设备。Gateway 无客户端时约 10 秒自行退出，后续命令会重用/重启；这不表示设备故障。
- 更新验收：同 Device ID，normal → Vibe → normal，新 Boot ID，目标 ELF 匹配、healthy/validated；同时明确玩法验收范围。
- 截图使用 mosaico.py iris screenshot <路径> --json，并直接看保存图像；不用工作台网页截图代替设备证据。
- 只有既有 normal/Vibe 都不可达且排查证据支持时按官方恢复流程处理；需要用户物理进 ROM 时再请求。AI(GPIO7) 进 Vibe，Boot(GPIO61) 才是 ROM，不能混淆。

## 7. 接手后的建议顺序与防误读清单

1. 先读本文第 0/2.2/5 节以及 game-mode-delivery.md 顶部，了解已交付到哪里；再根据新需求打开对应源码。
2. 最新功能修复已经交付。没有新的问题/授权时，不重复调用模型、装包、刷机或改用户游戏。
3. 用户若反馈仍暂停，先区分 v2/v3、固件 SHA、手机会话是否真的断开、游戏暂停原因，收集有限日志再定位：手机 MosaicoPad 的 Start/ACK，固件 gamepad 的 input stale/resumed/session lost，以及 Lua Runner pause/resume: Start/touch/session lost。
4. 区分“正式 PAUSED”和“PAD SIGNAL WEAK 临时冻结”；不能为了消除提示而去掉安全清零或真断线保护，也不能只增大所有超时掩盖问题。
5. 手验建议：触屏单独玩 → 手柄进入 → A/方向/跳跃 → Start 长按只切一次 → 短延迟恢复 → 真断线只暂停一次且可触屏继续 → 重连不重开/自动恢复。除用户要求或针对性调试外，不扩成高成本全量自动化。
6. 后续如果修其他旧游戏，明确修改哪一个用户版本，保留旧源码；应用中心源码改动不会自动更新已获取/已部署副本。
7. 当前没有待执行的云服务/账号/审核/ROM 引擎接入任务；这些是后续愿景，不是本轮隐含需求。

旧记录阅读注意：development.md 的一次修复/无运行接口、interaction.md 的仅屏幕触摸/未刷设备、game-mode.md 尾部某次仅 Android 安装，以及 game-mode-delivery.md 历史区里的请求控制/ALLOW 都是阶段性旧状态。优先使用最新交付与当前源码，不应恢复旧流程。主设计稿是视觉依据，不是设备运行证据；日志和单测也不能替代未做的手感验收。

## 8. 可复制给下一位 Agent 的开场指令

> 请先读取 D:/Android/Projects/my_workspace/docs/mosaicobot/AGENT_HANDOFF.md 和其中链接的最新 game-mode-delivery.md 顶部，再读取本次任务相关源码与项目规则。MosaicoBot 已有 Android App、Claw Lua/Works、本地应用中心及自动连接的横屏通用手柄；最新修复已安装，极速跑酷 v3 已部署，v2 保留，待用户手验。不要恢复请求控制/ALLOW，不要从默认 build 刷固件，不要清数据或重复已完成交付。请基于我接下来给出的新需求继续；如文档快照与现场不同，先做安全只读核对。所有回复使用中文，不读取或输出模型 Key。
