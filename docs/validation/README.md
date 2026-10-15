# Android Agent 验证证据

## 创作页对齐与 Works 真实运行（2026-10-12，最新）

本节覆盖之前“仅文件传输、未运行”的阶段限制；下方历史故障和原始记录保留，不改写其结论。

- 竖屏创作页与设计稿 v04 同步：应用图标、作品卡、运行/详情按钮、先填入再发送的建议、独立输入区；图标不是实时镜像。设备页保留能力概览、管理连接和独立 Claw 入口。横屏本轮不验收。
- Android 最新 APK 已覆盖安装，74 项单元测试通过；Lint 0 错误、13 警告。HTML 原型 91 项检查通过，含 14 个旧应用、返回路径、断线、手动确认和自动无障碍检查。
- 最初新增接口仍因 Works 只在打开页面时初始化而失败。修正后在 UI 启动前准备服务，在 app_claw_start 后挂接 registry；无需先打开设备 Works 页面。冷启动顺序源级检查通过，显式启停 host C 测试通过；包 ID 的 host C 可执行文件被 Windows 安全策略阻止，未绕过，不能算测试通过。
- 固件最终构建 20261012-203732-build-35524 成功（41.6 秒、1 条警告）。官方 System Update 操作 43858ce7-36e1-44cf-8635-ee97f624298b 成功：Claw 6294571811387877460 → Vibe 17813716137987680439 → Claw 8365868069558401274；目标 ELF e46a124adf3801943891043e57dbc49585f7a7d58f9e42eaad2118608f3676f2，healthy=true。bootloader 与分区表哈希未变，未擦除 NAND 或写入新的视觉场景。
- 手机中的现有「专注时钟」v1 成功完成：读回旧文件 → publish → start → 真实 running；CLI 截图实际显示 FOCUS TIMER 25 MIN、25:00、START/RESET/EXIT，不是网页模拟。手机 stop 后状态 stopped，设备恢复原充电锁屏；第二次 start 同样成功。
- 作品 ID 581f2da22d254b84、源码摘要 5e0c173a3b05619390d588fe9e9f5ae566b78c17b71df729b6176f9e5e4c5805 未变。未重新调用模型、恢复已删作品或改写用户 Lua。最终保留作品运行、计时器处于 PAUSED 待开始。
- 最后实时查询为同一 Device ID 4553502d49524953010030eda0f45ede、normal、stale=false、crash_count=0。没有据此宣称所有原应用或完整计时逻辑已验收。

证据：[结构化记录](works-runtime-v4-evidence.json)、[手机创作页](phone-creation-running-v4.png)、[设备实际画面](mosaico-focus-running.png)、[停止后画面](mosaico-focus-stopped.png)、[重新运行](mosaico-focus-rerun.png)、[设备 Claw 入口](phone-device-claw-v4.png)。设备截图均通过 mosaico.py 获取并直接检查。

尚未验证：计时 START/PAUSE/RESET/EXIT 触摸操作、倒计时持续推进、长时间运行、真机跨版本/断线中途写入。当前接口无安全配对、沙箱和跨终态 request_id 去重。硬件主页重设计仍为 [待确认视觉提案](../hardware-redesign/README.md)，未作为本次固件视觉资源交付。


## 真实手机：热点直连（当前验证）

2026-10-12，PZ261A 原生 MosaicoBot App，通过电脑热点访问模型服务。已关闭应用内 USB 代理、移除 ADB reverse，并停止开发 PC CONNECT 服务；HTTPS 与模型请求由手机发起，不经开发代理。电脑仍作为热点网络出口。

- 最小模型自检：真实回复，测试通过。
- 完整意图：生成贪食蛇。约 203 秒后通过本地 Lua 语法/lint 校验，保存 v1，共 232 行。
- 模型：step-5-preview:stepfun，reasoning_effort=low，输出上限 16384 token。
- 原始作品：phone-snake-v1.json；Lua 阅读副本：phone-snake-v1.lua。
- 原始 Lua SHA-256：67082fc7f0073c45fd6e02e09a1d5254c2fa0bdd61d4baec998bbca82dc42774。应对 JSON 的 lua 字段计算哈希，避免文件结尾换行差异。
- 强制停止、重启后，应用列表重新打开 v1；ID、版本、源码均不变，源码弹窗可以进入/返回。
- 密钥配置不含明文 Key，bootstrap 已清理，开发代理端口为 0。
- 尚未部署到 Mosaico，未验证触摸操作或设备运行；不能把语法通过当成游戏验收。

结构化记录：phone-direct-evidence.json。截图：../android-phone-direct-check.png、../android-phone-created.png。

## Mosaico ROM 分区核验

通过 mosaico.py recover --dry-run --inspect-layout，在 Gateway 排他操作中核对硬件 MAC 30:ed:a0:f4:5e:de，并读取已知分区表位置。结果见 mosaico-rom-layout.json；操作 ID：d6747fa1-3a8b-4fed-84ea-db09223a5b0f。

0x9000 的有效分区表确认是旧 Claw 布局，现有 NVS 位于 0xA000。Vibe 基础恢复包的 otadata 会覆盖 NVS 首个 4 KiB，因此当时暂停；本检查未读取配置内容。随后已取得明确覆盖授权并执行下述刷写。旧布局证据不代表刷后当前状态。

## Mosaico 覆盖刷写与实时验收

用户授权后，通过 mosaico.py recover 写入 reviewed Vibe 0.1.4；无整片擦除。写进程返回 0，镜像写后哈希通过。原操作 d9f883e4-9eae-4e10-9cee-44637520437e 的自动验收因旧串口助手占用 COM19 而超时，终态 outcome_unknown 如实保留。

停止该旧助手后，正式 CLI 成功取得同机实时状态：Device ID 4553502d49524953010030eda0f45ede、Boot ID 16844240985224958878、Vibe 0.1.4、ota 可用、stale=false。截图已直接检查，显示 Vibe Mode / USB Active / Network Offline。

结构化证据：mosaico-flash-evidence.json；截图：mosaico-vibe-after-flash.png。该阶段仅证明 Vibe 就绪；后续 Claw 安装与故障见下节，手机作品仍未运行。

## Claw 首次安装与重复启动故障

首次 System Update（3512eade-2de3-4356-9bf6-e62bdbc8f71f）成功，普通重启成功；直接截图 mosaico-claw-after-update.png 是 Claw 联网引导页，并非游戏或原应用交互验收。

两次 normal → Vibe → normal 操作分别为 f1754848-9f64-48a3-995a-b58434135720、63d7d5eb-49e2-48ef-8359-4aa66a9bca6b，均在 SPI/DMA 初始化时崩溃并回退 Vibe。第二次已调整屏幕缓冲分配顺序但没有改善，实验撤回。对应现场：

- mosaico-claw-startup-crash.json / .bin / -decoded.txt；匹配 ELF：logs/mosaicobot-claw-first.elf。
- mosaico-claw-second-crash.json / .bin / -decoded.txt；匹配 ELF：logs/mosaicobot-claw-mirror-order.elf。
- 第二次失败 Boot ID 16270455175497073770，回退 Vibe Boot ID 1997526140947516563；两次原操作的 failed 终态不改写。

DMA 软重启清理实验也未解决问题：首次安装成功，往返 021eace6-5b9d-4d44-97e1-f912a774353e 仍失败。记录为 mosaico-dma-install.json、mosaico-dma-roundtrip-failed.json、mosaico-dma-crash.json / .bin / -decoded.txt；匹配 ELF 为 logs/mosaicobot-claw-dma.elf。该 SDK 实验已撤回，原始补丁仅留在 experiments 供复盘，不应当作构建前提或有效修复。

诊断版 build8 已确认 USB DMA 覆盖指令：app_main 一开始，`0x2f01b144` 的 8 字节已变为 `80 06 01 03 00 00 ff 00`，恰好是 USB GET_DESCRIPTOR（字符串描述符 1）的 SETUP 包。Iris 启动前后、trial_auth 前后的三次样本一致；尚未进入 Board Manager。TinyUSB 的独立 DWC2 DMA 仍持有上个固件的 EP0 buffer 地址。

证据：mosaico-probe-instructions.json、mosaico-probe-crash.json / .bin、mosaico-probe-roundtrip-failed.json；匹配 ELF 为 logs/mosaicobot-claw-probe.elf。原失败操作 f8b67745-e708-45cd-a2ed-02dc165adbbf 保持 failed。

USB 专项修复在重启前断开设备、等待 AHB 空闲、复位 USB 核心并关闭 DMA，仅处理已开时钟且启用 DMA 的 USB device mode。必须同时构建并更新 Claw 和 Vibe，单独更新 Claw 不足以修复旧 Vibe 的退出行为。临时诊断探针已从正式候选移除；固件认证、分区和既有 camera 修复保持不变。构建与寄存器替身测试不能替代后续真机验收。

## USB 修复版实机验收（2026-10-12）

- Device ID：4553502d49524953010030eda0f45ede；MAC：30:ed:a0:f4:5e:de。
- Vibe 自更新 421757c8-b0e7-481f-9d83-cdeecd6b556e 成功，Boot ID 17936892931273864315。仅 recovery 组件；bootloader 与当前 Claw 分区表哈希不变，评审 prebuilt 未替换。
- Claw 安装 924397c1-0dc2-4f1c-96b2-62d03955e7cb 成功，Boot ID 14893961040453638176，healthy=true。
- 第一轮：Claw 14893961040453638176 → Vibe 5602160344773656651 → Claw 2655544889624839517；返回操作 b7def6e0-503c-4baa-932b-919f918b5133 成功。
- 第二轮：Claw 2655544889624839517 → Vibe 15596648976138166412 → Claw 757618137545933408；返回操作 391b9dac-56e0-4db5-a619-71e31424a92e 成功。
- 普通重启 188f6c42-e3ba-490b-a3ab-98234c182854 成功，新 Boot ID 10093071470808488632。以上每次固件切换均核对同一身份、新 Boot ID、目标 ELF 哈希、健康状态及不变的 bootloader/分区表。
- Claw ELF SHA-256：3467b18a429655b94578e5740a70a7e909ca02c20a66e26892fb1383fa77fbc6；Vibe ELF SHA-256：d5f9edc929a64b6cd6a7d6095ac4224b2b472c628a6d747209aead6b0ad0bf92。
- 截图 mosaico-claw-usb-fixed.png 已直接检查，显示时钟/天气卡片和 Camera、CLAW、Works 主屏入口，并非之前的联网引导页。日志记录 STA 地址 192.168.137.246（电脑热点子网）；未据此声称手机端发现/连接已验收。
- 构建信息 mosaico-usb-builds.json；专项测试 267 项、39 子测试通过。Vibe 固定槽剩余 14,144 字节，容量警告如实保留；两次增量构建均成功。
- 单次失败历史和被否决实验保留；原 failed/outcome_unknown 操作不改写。手机“生成贪食蛇”的源码已经保存，但固件 Works 事务发布、远程启动/停止仍未实现。原应用逐项交互、长时间运行及手机作品真机行为仍待验收。

原始记录以 mosaico-usb-*.json 及 mosaico-claw-usb-fixed-screenshot.json 保存。最终状态与短时观察详见 mosaico-usb-acceptance.json。

## 手机局域网自动发现与连接（2026-10-12）

原生 App 最新 APK 已覆盖安装至 PZ261A；手机 192.168.137.67/24 与 Claw 192.168.137.246 位于同一电脑热点。未手填地址即自动发现并连接，读取到 /nand 与 28 个 Lua 模块。网络关闭/恢复、手动扫描/停止、自动发现开关持久化、关闭自动发现后的手动扫描、手填连接及返回前台扫描均已通过实际界面验证。

- 最终截图（已直接检查）：[phone-discovery-final.png](phone-discovery-final.png)；对应 phone-discovery-final.xml。自动开关开启、扫描已完成、已连接 1 台设备。
- 首次自动连接：phone-discovery-auto.xml / .png。
- 手动扫描：phone-discovery-manual-scanning.xml、phone-discovery-manual-connected.xml。
- 断网/自动重连：phone-discovery-disconnected.xml、phone-discovery-network-reconnected.xml。Wi-Fi 已恢复开启。
- 关闭自动发现并重启确认：phone-discovery-auto-disabled-verified.xml；关闭后手动扫描：phone-discovery-manual-auto-off.xml。
- 手填地址连接：phone-discovery-manual-address.xml。测试结束已恢复自动发现开启。
- 结构化记录：[phone-discovery-evidence.json](phone-discovery-evidence.json)，含 APK 摘要、57 项单元测试与 Lint 0 错误/13 警告记录。

原有手机贪食蛇 v1 的 ID、版本与源码摘要不变。本轮扫描只读设备接口，无固件更新、作品上传或启动。当前连接是接口验证而非安全身份配对，也没有持续心跳；多台实机、仅 mDNS 可达、IPv6-only 和路由器隔离等场景未实测。前述 USB 验收阶段未验证手机连接的限制保留为历史记录，本节是后续独立验收。

## 模拟器：早期验证

2026-10-12，在 API 36.1 Android 模拟器原生 MosaicoBot App 中发送「生成贪食蛇」，由已授权模型 step-5-preview:stepfun 返回。

- 版本：v1
- 源码：snake-v1.lua，共 285 行
- SHA-256（App 中原始 Lua 字符串）：98c13e92821178e53636ebef52e1a60689c8e9dc0aa95d45d391d390c96a91bf
- 结果：手机 Lua 语法检查与保守 lint 通过，作品保存成功。
- 硬件状态：未部署、未运行。源码有食物生成、方向输入、碰撞、暂停、重开与退出逻辑；这些行为尚未在 Mosaico 验收。
- 注意：文件结尾可能多一个换行，计算对比时请使用 App 原始字符串或移除该终止换行。这份源码是模型生成的验证产物，不是人工保证正确的固件示例。

对应原生界面截图见 ../android-created.png 与 ../android-apps.png。
