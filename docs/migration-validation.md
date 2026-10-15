# 独立目录迁移验证（2026-10-14）

新集成入口：`projects/mosaicobot/`。原固件、Android、Vibe 和文档目录未删除，未清理既有用户改动。

## 新路径验证结果
- Android：`:app:testDebugUnitTest :app:assembleDebug`，`BUILD SUCCESSFUL in 53s`，45 个任务实际执行。JUnit 206 tests / 0 failures / 0 errors / 0 skipped。
- APK：`mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk`，63,333,799 bytes。
- 固件 host：`pytest tools/tests components/mosaic_ui/common/test_scene_assets.py -q --tb=short`，14 passed、2 subtests passed。复用现有 Python 测试解释器，但运行的是新目录源码。
- 固件：新建 `build-mosaicobot`，未复制旧 build；pinned IDF `7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe`、esp32s31、GSPC 0.6.1。最终 `IDF LOW-NOISE BUILD: OK`、exit 0、1393.2 秒、20 warnings。
- 固件 BIN：6,598,256 bytes。完整日志：`mosaicobot-firmware/.codex-runs/idf-low-noise-build/20261014-204545-build-44352/raw.log`。
- SDK doctor：target_supported yes；工具对 manifest IDF 约束报告 unresolved/unverified，不代表切换了 SDK。
- 更新包通过官方 ESP-Iris bundle inspect：partition table、application、ui_apps、system 四部分；不包含 bootloader、NVS 或 NAND 镜像。`signature_verified:false`，不是签名发行版。
- PowerShell 构建/安装入口语法解析通过；CLI 的 claim/crash/logs/system-update 参数已只读检查，日志使用 bounded snapshot。

## 迁移修复
保留 board-generated 源码并解除其原 `*` 忽略规则；组件依赖改相对路径，板级资源不再解析带绝对路径的 CMake 输出。修复复制排除规则误漏 Gradle 与 build_progress.py。固件默认 target 补为 esp32s31，独立准备 ESP-Iris 打包 Python 环境，不使用旧虚拟环境。定制 utils 普通源码纳入，干净 esp-claw/BSP/raylib 注册固定 commit 官方子模块。

## 后续完成情况（2026-10-15）
新目录 APK 和固件已安装并完成核心实机验收，见 `new-directory-device-acceptance.md`。旧三个工程目录已移除；旧文档内容已清空，空目录暂被系统进程占用。Git 历史和遗漏/旧版本文件已私有归档到 `.migration-backup/`。公开仓库 https://github.com/PlepleLiang/mosaicobot 已创建，提交者确认公开发布权限；源码上传结果以后续远端核对为准。

## 构建阶段历史状态
本次没有再次给设备刷机或安装新 APK；此前实机验收是迁移前版本，不能冒充新产物验收。新目录源码 Git 已初始化，依赖 gitlink 暂存；尚未 commit/push、未创建远端仓库。

官方 GitHub MCP v2.0.2 已安装、官方 SHA256 校验并注册；StepCode 要求重启加载，当前尚未完成用户浏览器 OAuth。公开发布前须确认 `THIRD_PARTY.md` 所列源码许可及素材/历史截图隐私；高置信凭据文本扫描 1859 候选文件无命中，不等于完整安全审计。历史 raw crash 与文本/JSON 运行时证据已从公开候选 Git 忽略，原始证据未删除。
