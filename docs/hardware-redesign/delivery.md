# 系统界面 06 · 交付记录

2026-10-13。按用户要求直接同步主稿、实现固件、构建并通过官方系统更新流程交付；不运行模拟器或浏览器全量验收。

## 变更范围

- 主稿 index.html 内直接呈现九屏可交互预览，右侧 Canvas 同步数字时钟和新应用卡片；手机布局不变。
- Hub 首页、两页原应用、下拉控制栏、通知、息屏、充电和模块插入采用石墨 / 信号橙视觉。
- Works 更新卡片、标签和状态文字样式，保留 Recent / Installed、四行分页、原启停与应用返回。
- 新图标在构建时抗锯齿生成，沿用 GSP 图片资源管线；仅补充数字时间、滑杆数值、电量等显示绑定。
- 原应用内部业务、LAN / 手机 Agent、Vibe、分区布局和用户作品不在本轮改造范围。

## 现有测试

直接调用现有 11 项单元测试（当前 Python 未安装 pytest，使用标准库 unittest 承载），10 项通过。

1 项不可完成：test_phone_works.py 的不可变包 ID 测试编译成功，但生成的 ids.exe 被 Windows 安全软件拒绝执行（WinError 5 / 225）。未添加白名单、关闭防护或绕过策略。场景图标参数冲突已修复并重跑通过；文本读取使用 Python UTF-8 模式。

## 构建

- IDF：固定提交 7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe，目标 esp32s31。
- 匹配的本机 GSPC：0.6.1；通过 GSPC_EXECUTABLE 选择缓存版本，未修改上游下载器。
- 低噪声构建结果：OK，exit 0，2 条警告。
- Run：20261013-111316-build-32688。
- 日志：projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-111316-build-32688/raw.log。
- 应用：build-mosaicobot/edge_agent.bin，6,590,592 bytes。
- 更新包：build-mosaicobot/edge_agent-system-update.irisfw。
- ELF SHA-256：3fc29ba06df3952792380efaace216d492071b007e9db5c2f906e2073c44c115。

## 官方系统更新

- Device ID：4553502d49524953010030eda0f45ede。
- Operation ID：7049afec-d72c-4ebb-9360-cff8405d9095。
- 更新前 Boot ID：8365868069558401274。
- Vibe 写入 Boot ID：6782260086490574429。
- 日志：projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T031727Z-system-update/raw.log。
- 状态：succeeded；官方回执 healthy=true、validated=true，实际 ELF SHA 与本次构建一致。
- 更新后 Boot ID：10710229242186343663；复查为 normal / idle、stale=false、crash_count=0。
- 最终状态日志：projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T031929Z-device-status/raw.log。
- Bootloader 与分区布局 SHA 更新前后相同；未整片擦除，更新清单不含 Vibe、NVS 或用户 NAND 数据。旧 coredump 由官方更新流程自动保存。

## Wi-Fi 图标微调 · 2026-10-13 11:29

仅修改 Hub 场景生成器：状态栏五档 Wi-Fi 图标从 40×40 缩为 24×24，原槽位内偏移 (8,8) 居中；首页与下拉栏同步，Wi-Fi 开关不变。原设计稿 21px SVG 已是目标视觉比例，不改其余画面。

- 现有 UI 单元测试：5/5 通过；未运行模拟器或额外交互检查。
- 构建：OK，exit 0，2 条警告；产物 projects/esp-mosaico-claw/build-mosaicobot/edge_agent.bin。
- 构建日志：D:/Android/Projects/my_workspace/projects/esp-mosaico-claw/.codex-runs/idf-low-noise-build/20261013-112649-build-44020/raw.log。
- 同一设备：4553502d49524953010030eda0f45ede；系统更新 succeeded，healthy=true、validated=true。
- Operation ID：155b1010-ac3b-4b77-bf12-9c962a4c6544；新 Boot ID：11854559113351912216。
- 固件 ELF SHA-256：3d62145377784870da77879201a543ccad9d5ae0c38ea00f23db0812c06ecda5。
- 刷写日志：D:/Android/Projects/my_workspace/projects/esp-mosaico-vibe/.codex-runs/mosaico/20261013T032754Z-system-update/raw.log。
- 图标尺寸由用户手动验收，其余已确认布局与业务保持不变。

## 手动验收

按用户要求，不声称逐项体验已验证。请检查方屏比例、时钟 / 天气、应用翻页和返回、Works 启停、下拉控制栏、锁屏 / 充电与模块插入。通知仍沿用现有通知逻辑；RESERVED 控件不代表新能力。
