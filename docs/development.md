# MosaicoBot 开发记录

更新时间：2026-10-12。原型交互见 interaction.md；本文件记录真实实现，不能把浏览器设计稿的模拟部署/运行状态当成产品能力。

## 当前进展（2026-10-12，设计 v04）

创作页、设备页已同步设计基线并安装到手机。已补齐 /api/works 的发布、查询与显式启停；修复运行服务依赖先打开 Works 页面的初始化问题。真实手机中现存专注时钟 v1 已在 Mosaico 显示，停止与再次运行通过，源码未变。详细固件操作、截图、74 项 Android 测试和 91 项原型检查见 [最新验证记录](validation/README.md)。

当前为可信局域网开发版，每个版本确认后运行。安全配对、设备端沙箱、首帧回执和长时行为验收仍待建设。硬件视觉重设计仅为待确认提案，本次未改原有场景和下拉通知栏。以下第一阶段记录和故障历史按发生时状态保留。

## 第一阶段交付（历史）

原生 Android 工程位于 projects/mosaicobot-android，包名 dev.mosaicobot，Android 11+。创作、应用、设备三页沿用已确认的信息架构。

| 模块 | 当前实现 | 尚未完成 |
| --- | --- | --- |
| 手机 Agent | 真实 chat/completions、最小连接自检、上下文修改、一次修复、取消 | 流式输出、语音输入、多步骤工具调度 |
| 作品 | Lua 语法检查、保守模块 lint、本地版本、源码查看 | 固件沙箱验证、执行预算、真机行为测试 |
| 原有应用 | 11 个主屏入口 + 3 个 Works 作品的源码目录映射 | 从硬件读取实际安装清单、远程打开/停止 |
| Claw 连接 | 同局域网自动发现并连接、前台/网络变化触发、手动扫描/停止/地址入口、状态与模块验证 | 安全配对、稳定身份绑定、持续在线检测 |
| 文件传输 | 独立版本目录、读回 SHA-256、清单最后写入 | 事务安装、发布/重扫、启动/停止、失败回滚 |
| 运行反馈 | 明确标记未运行/未确认 | 运行事件、首帧、错误日志、屏幕同步 |
| Vibe | Claw 0.4.0 集成保留 Vibe；USB 修复版完成两轮模式往返、普通重启、健康与主屏截图检查 | 手机侧 Iris、原有应用逐项交互与长期稳定性验收 |

**目前不是“说完立即在硬件运行”的完整版本。** 没有远程启动接口时，手机不会虚构启动成功。Claw 原有应用源码和资源保留。USB DMA 覆盖下一固件指令的问题已定位；Claw 0.4.0 与 Vibe 0.1.4 的专项修复版均已安装，通过两轮 Claw → Vibe → Claw、普通重启及固件健康检查。设备主屏已直接检查，包含 Camera / CLAW / Works 入口。原有应用逐项交互和手机作品远程发布/运行尚未验收，不能等同于完整产品闭环。

## 模型配置

参考本机 StepGo/StepCode 的 https://models-proxy.stepfun-inc.com/v1。对用户提供的 Key 实测，gpt-6-astra 不可用（403），step-5-preview:stepfun 可用，因此默认使用后者与 /chat/completions。

Key 不写入源码、APK、文档或命令参数；Android 端由 Keystore AES-GCM 保存，已在真实手机 PZ261A 安全导入，临时 bootstrap 文件已删除。模型输出使用非流式请求；连接 12 秒、模型读取 300 秒、单次总计 360 秒，允许取消；一次校验修复是第二次有界模型请求。默认 step-5-preview:stepfun 使用 reasoning_effort=low、16384 token 输出预算；其他自定义模型不发送此选项。设备 HTTP 使用独立的较短预算。失败不会保存残缺应用或静默重复请求。

手机最初无法解析内部模型域名，曾使用限定目标域名的开发 USB CONNECT 通道。接入 PC 热点后，已关闭应用内代理、移除 ADB reverse、停止本任务的 PC 转发服务，手机 HTTPS 直连模型的最小请求与完整生成均成功。当前仍依赖此热点提供网络可达性；不能推断普通移动网络也可访问内部服务。

## 已验证的固件依据

来自 esp-mosaico-claw 的实际代码：

- components/http_server/http_server_status_api.c：/api/status。
- components/http_server/http_server_lua_modules_api.c：/api/lua-modules。
- components/http_server/http_server_files_api.c：目录列举、mkdir、upload、/files 读回。
- components/mosaic_ui/common/works_runtime.h：本地 Works 生命周期，不等于 HTTP 远程生命周期接口。
- fatfs_image/system/apps/dino/launcher.json 与 scripts/dino.lua：作品布局参考。
- third-party/esp-claw/components/lua_modules/lua_module_display/README.md：display 对象 API。
- third-party/esp-claw/components/claw_capabilities/cap_app_mgr/skills/app_creator/references/app-package-spec.md：应用包 schema v1。

DATA 位置由状态读取，不写死 /fatfs；文件接口按 DATA 相对路径 /apps 操作。每个版本使用 mb_<随机作品ID>_v<版本>_<源码摘要>，避免 DATA 同 ID 遮蔽 SYSTEM 应用。手机不调用未经证实的 launch/stop/publish 路由。

## 验证记录

- Gradle 构建通过，产物 app/build/outputs/apk/debug/app-debug.apk。
- 最新 57 项 JVM 单元测试通过；包含 HTTPS 测试服务、模型预算、开发代理隔离、传输 mock、子网边界与连接状态机，不代表真实硬件测试。开发 CONNECT 工具此前另有 6 项 Node 测试通过。
- 最新 Android Lint 0 错误、13 个非阻塞警告（依赖版本和 KTX 建议），具体以 app/build/reports/lint-results-debug.html 为准。
- API 36.1 模拟器安装和启动成功；三页原生界面、Keystore 配置导入已检查。
- 模拟器上的最小模型连接测试取得真实回复。
- 第一次完整贪食蛇请求在原 120/150 秒网络预算下失败；没有作品被保存。第二次请求确认为 8K 输出上限截断，残缺作品同样未保存，输入恢复。
- 模型预算调为 16384 token 后，从原生界面发送“生成贪食蛇”成功返回完整应用，通过本地语法/lint 校验并保存 v1（285 行）；本次耗时约 3 分钟，尚非即时生成体验。
- 升级 APK 并重启后，从“应用 → 我的创作”重新打开 v1，源码与保存前一致。源码弹窗可读；未连设备时传输按钮不可用。
- 源码和原生截图见 validation/README.md、android-created.png、android-apps.png。原有应用目录滚动及两类 Album 标识已检查。
- 已核验模拟器配置不包含明文 Key，bootstrap 临时文件已删除；源码扫描未发现本次用户 Key。

真实手机 PZ261A：用户关闭安装监控后，ADB 安装和覆盖升级均成功，未绕过系统限制。

- 首次通过开发 USB 通道的完整生成被模型截断，没有保存残缺作品；随后的一次 USB 请求在用户切换热点后主动取消。
- 关闭 USB 开发通道后，手机直连最小请求取得真实回复。
- 从原生界面发送「生成贪食蛇」，约 203 秒生成 232 行代码，通过语法/lint 并保存 v1。源码 SHA-256：67082fc7f0073c45fd6e02e09a1d5254c2fa0bdd61d4baec998bbca82dc42774。
- 强制停止并重新启动 App 后，在「应用 → 我的创作」重新打开；作品 ID、版本和 Lua 完全一致，源码弹窗可正常进入和返回。未连接 Mosaico 时传输按钮不可用。
- 已核验配置中无明文 Key、开发代理端口为 0、临时配置已删除。真机证据与旧模拟器证据分开保存，见 validation/README.md。

Mosaico：用户进入 ROM 下载模式后，已通过产品工具确认 COM18、硬件 MAC 30:ed:a0:f4:5e:de；操作前无其他 Gateway 占用。ROM 没有 Iris Device ID/Boot ID，不能把旧缓存版本 0.1.4 当成板上当前状态。

为判断数据保留条件，在 utils 主机工具增加 recover --dry-run --inspect-layout，只通过 Gateway host.probe 读取两个已知分区表位置、重新核对 MAC，禁止写入和复位。实际操作 d6747fa1-3a8b-4fed-84ea-db09223a5b0f 确认：

- 0x8000 无有效分区表；0x9000 有通过 MD5 校验的旧 Claw 表。
- nvs：0xA000 / 0x6000；otadata：0x10000 / 0x2000；factory：0x20000 / 8000 KiB；ui_apps、system 也与 Claw 源码布局一致。
- reviewed Vibe 0.1.4 的 otadata 写入覆盖 [0x9000, 0xB000)，与现有 NVS 的首个 4 KiB 重叠。直接 recover 会损坏现有配置；当前 Claw 本身也尚未集成保留 Vibe 的运行与分区契约。
- 上述检查阶段未写入。随后用户明确授权「直接覆盖刷写 Mosaico」，已按下节执行；不能再将设备描述为未刷写或等待迁移授权。

主机工具专项验证：Claw profile、mosaico-tools/tests 加 recovery 的 test_cli.py、test_command_tree.py、test_recovery_flash_mode.py，共 264 项测试及 39 项子测试通过。原有 UI 场景资源另有 5 项测试及 2 项子测试通过。更广的固件测试未全通过：本机仍缺少部分固件/UI 测试依赖，并存在 C 产物执行、仿真及 GBK 解码环境问题；不能据此宣称所有固件测试通过。

### 已授权覆盖与 Vibe 实机验收

- 使用受管 mosaico.py recover 写入 reviewed Vibe 0.1.4；没有整片擦除。ROM 模式首次默认复位失败，未写入；修复仅针对已在 ROM 的目标使用 before=no-reset，保留 after、安全参数与镜像原样。
- 实际写入操作：d9f883e4-9eae-4e10-9cee-44637520437e。写进程返回 0，factory 1,817,040 字节写后哈希校验通过。
- 原操作刷后验收超时，终态 outcome_unknown，记录不改写为成功。发现旧 mosaico-console-helper.py 自 10 月 9 日运行并实际占用 COM19；仅停止该旧助手后，通过正式 CLI 取得新实时验收。
- 实时 Device ID：4553502d49524953010030eda0f45ede；MAC：30:ed:a0:f4:5e:de；Boot ID：16844240985224958878；firmware_mode=recovery；app_version=0.1.4；ota 能力可用；stale=false；crash_count=0。
- 截图已直接检查：Vibe Mode、USB Active、Network Offline。记录见 validation/mosaico-flash-evidence.json、validation/mosaico-vibe-after-flash.png。
- 旧 Claw 主程序和原布局已被基础固件覆盖；这不等于 Claw 应用交付。原应用源码和资源保留，独立 build-mosaicobot 与 tools/cmake/mosaicobot_profile.cmake 已完成接入，前 2 MiB 保留，主应用和 UI/SYSTEM 资源使用 System Update。

### Claw 安装和模式切换故障

- 首次安装 3512eade-2de3-4356-9bf6-e62bdbc8f71f 成功，Claw Boot ID 13077170645963200871；普通重启 69c48aee-9fd2-4bdd-9fc5-4108d96ce389 成功，新 Boot ID 6240611521130653847。
- 直接截图 mosaico-claw-after-update.png 显示原有 Claw 联网引导页；它不证明原应用交互通过。
- 往返 f1754848-9f64-48a3-995a-b58434135720 失败，回退 Vibe。把可选屏幕缓冲分配移到 Board Manager 后，再安装成功，但往返 63d7d5eb-49e2-48ef-8359-4aa66a9bca6b 再次同点失败。该假设已排除，实验已撤回。
- 两份有效 core 和匹配 ELF 已保留。相同栈：esp_cache_get_line_size_by_addr → gdma_new_link_list → spi_bus_initialize → esp_board_manager_init。证据见 validation/mosaico-claw-startup-crash.json、mosaico-claw-second-crash.json 及对应 decoded.txt。
- 固定 SDK 的 S31 esp_system_reset_modules_on_exit 没有 DMA 清理，而同版本 P4 明确要求清理以免重启后内存被破坏。局部 S31 HAL 实验补丁保留认证与加密行为；其构建 20261012-183932-build-49108 成功（41.4 秒、1 条 factory 容量警告，应用实际目标为 ota_0），本机清理顺序测试通过。
- DMA 实验首次安装 96324e22-2968-4fe3-a161-fd287c778a79 成功，但往返 021eace6-5b9d-4d44-97e1-f912a774353e 仍同点崩溃：失败 Boot ID 7653592650565929938，回退 Vibe Boot ID 6317251185522289942。补丁未解决问题，已从 SDK 撤回，保存为 validation/experiments/idf-7b9cc1ac-s31-restart-dma-rejected.patch。匹配 ELF 和 bundle 在 logs/mosaicobot-claw-dma.*，core 与解码在 validation/mosaico-dma-crash.*。
- 该实验阶段主机专项 265 项及 39 子测试通过；首次误用 IDF Python 因缺少 aiohttp 失败，改用受管 Iris Python 后通过，不是业务代码修复。无效实验测试从产品测试目录撤回。
- 第三份异常中 PC 位于指令中间，且寄存器不符合已编译函数入口行为。诊断构建 build8 在 app_main 入口即捕获 `0x2f01b144` 被 `80 06 01 03 00 00 ff 00` 覆盖；这正是 USB GET_DESCRIPTOR SETUP 包，确认 USB 独立 DMA 沿用旧 EP0 buffer 地址。见 validation/mosaico-probe-instructions.json。
- 因此改为 USB 专项 SDK 修复，在 CPU-only restart 前使 DWC2 总线空闲、复位并关闭 DMA；Claw 与 Vibe 必须同时包含修复。此前 GDMA 清理不是有效修复。正式候选已移除只读探针，保留原应用、认证与安全配置。

### USB 专项修复验收结果

Claw 与 Vibe 修复版均已安装；两轮 Claw → Vibe → Claw 和普通重启通过，设备身份一致、每次 Boot ID 更新，目标固件哈希和 healthy 验证通过，bootloader 与分区表保持不变。最新真机画面是原有主屏（Camera / CLAW / Works），日志取得热点局域网地址 192.168.137.246。详细操作编号、哈希、截图和有限验证范围见 validation/README.md 的 USB 修复版实机验收，以及 validation/mosaico-usb-acceptance.json。此前四次往返故障均作为历史证据保留。

构建专项 267 项测试和 39 子测试通过；这不包含整个固件测试集，也不替代原应用逐项交互和手机作品真机验收。

## 局域网自动发现与连接（2026-10-12）

已实现并安装到真实手机。默认开启自动发现：启动/回到前台以及 Wi-Fi/以太网网络变化触发有界扫描；设备页提供手动扫描、停止、开关记忆、发现列表切换与手填地址。优先上次成功地址，否则连接首个通过 Claw 状态及基础 Lua 模块校验的设备。

mDNS 与 IPv4 探测互为补充。探测范围为当前私有子网内的本机 /24 窗口或更小子网，最多 254 个目标、12 并发、总预算 22 秒；mDNS 地址同样限制为当前私有子网。只读 /api/status 和 /api/lua-modules，不上传或运行作品。设备连接使用独立且绑定局域网的 HTTP 客户端，不改变手机模型请求；断网取消设备上传，拒绝旧扫描的迟到结果。退出前台停止扫描，没有后台常驻服务或持续心跳。

真实手机 PZ261A（192.168.137.67/24）和 Mosaico（192.168.137.246）同在电脑热点：

- 不手填 IP，自动发现 1 台并连接；读取到 /nand 与 28 个 Lua 模块。
- 手动扫描并自动连接；停止扫描有效；关闭自动发现后仍可手动扫描和手填地址连接。
- 关闭 Wi-Fi 后旧连接和列表清除；恢复 Wi-Fi 后无需点击即可自动重连。
- 自动发现关闭设置在强制停止/重启后保留；返回前台会在开启自动发现时触发扫描。
- 最终 Wi-Fi 和自动发现均开启，扫描已完成且设备已连接。安装升级后贪食蛇 v1 的 ID、版本与源码 SHA-256 保持一致。

最新 APK、测试计数、逐项 UI 证据和范围限制见 validation/phone-discovery-evidence.json，最终截图见 validation/phone-discovery-final.png。本轮未修改固件、未重新调用模型、未上传或启动作品。多台实机、仅 mDNS 可达网络、IPv6-only 与路由器隔离场景未实测。当前 Claw HTTP 不提供安全身份认证；地址记忆和接口校验不等于配对。

## 当时规划：闭环接入（下列生命周期桥接现已实现）

Vibe 与兼容 Claw 的 USB 修复版已完成两轮同机模式往返和普通重启验收，下一步接入手机运行闭环。旧串口助手占用已排除，重启指令覆盖也有独立 core 证据和针对性修复；没有通过反复重烧掩盖故障。

1. **设备身份与配对**：稳定 Device ID + Boot ID、固件/协议版本、短时配对确认、凭据隔离与撤销。
2. **作品事务**：暂存、校验、提交、registry 发布、幂等 request ID、明确版本。保护 SYSTEM 应用；失败不使半包成为可运行作品。
3. **生命周期桥接**：把已存在的 Works 运行时安全暴露为认证的查询/启动/停止操作；在 LVGL/运行时规定线程执行，HTTP handler 不直接操作界面。
4. **执行安全**：模块白名单必须由固件 enforce；代码/内存/时间预算、取消、错误退出和 display 清理，不能依赖手机正则检查。
5. **真实反馈**：设备返回 request ID、作品 ID、版本、Boot ID 与运行状态；首帧或截图、结束原因、错误日志分别显示。只有真实 running 回执才能显示运行中。
6. **保留原应用**：读取 Native Launcher 与 Works 两套目录，系统/预装/用户应用分别授权；手机的 Agent 和设备 CLAW 不互相冒充。
7. **验收**：真手机发“生成贪食蛇”→生成→提交→运行→触屏控制→停止→修改版本→再次运行；断网、超时、坏包、重启等失败路径也需通过。

上述是后续实现顺序，不是已经存在的 API 路由或已完成能力。连接协议和安全边界确定后再把首版的手动确认传输升级为“授权后的自动部署运行”。
