# MosaicoBot

内部参赛项目：Android 手机端负责创作、保存与部署 Lua Works，Mosaico 设备负责本地执行、系统应用与横屏手柄交互。

## 展示与体验网站

参赛展示站源文件：[docs/site/index.html](docs/site/index.html)。包含手机背贴、掌机联屏、独立挂件三种形态，以及九种可玩模板和模拟创作闭环；保留原交互文档入口。网页不调用真实 AI、Lua 或硬件。

GitHub Pages 目标地址：https://plepleliang.github.io/mosaicobot/ （首次部署成功后可访问）。[预览、验收与发布说明](docs/site/README.md)。

## 工程目录
- `mosaicobot-firmware/`：基于 ESP-Mosaico Claw 的设备固件；固件标识仍为 `edge_agent`，目录改名不改变升级协议。
- `mosaicobot-android/`：原生 Kotlin / Jetpack Compose 手机应用。
- `esp-mosaico-vibe-custom/`：本地定制 Vibe 与实际修改过的 utils/ESP-Iris/Recovery 工具源码。
- `docs/`：交互原型（打开 `index.html`）、开发、设计、功能交付与历史验收。
- `tools/`：构建、安装入口和来源记录。

本目录是唯一的集成入口；旧固件、Android 和 Vibe 工程已在新产物实机验收后移除，原 Git 历史和诊断证据保存在本机 Git 忽略的 `.migration-backup/`。不依赖旧目录的 build、虚拟环境或模型配置。历史文档中的旧目录、IP、设备 ID、Boot ID 仅是当时记录，不能当作现场实时证据。

## 初始化与依赖
源码中的官方依赖固定 commit；使用 `git clone --recurse-submodules https://github.com/PlepleLiang/mosaicobot.git`，或执行 `git submodule update --init --recursive`。

Windows 构建环境：Git（Git Bash 默认安装路径）、Python 3.10+、JDK 21、Android SDK 36；固件 target 为 **esp32s31**，ESP-IDF 必须为 commit `7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe`，GSPC 0.6.1。IDF 必须有该 SDK 对应的 Python 环境与工具，不能用其他芯片/版本代替。初次依赖下载需要网络。

```powershell
$env:JAVA_HOME = '<JDK 21 目录>'
$env:ANDROID_HOME = '<Android SDK 目录>'
$env:IDF_PATH = '<固定 commit 的 ESP-IDF 目录>'
$env:GSPC_EXECUTABLE = '<GSPC 0.6.1 可执行文件>'
# 使用固定 SDK 的 Python；后续命令中替换 python 为其完整路径。
python -m pip install -r mosaicobot-firmware/requirements.txt
python -m venv esp-mosaico-vibe-custom/.tools/iris-host
esp-mosaico-vibe-custom/.tools/iris-host/Scripts/python.exe -m pip install -r esp-mosaico-vibe-custom/submodule/esp-mosaico-utils/ESP-Iris/components/esp_iris/tools/requirements.lock
```

构建：
```powershell
./tools/build.ps1 -Target android
./tools/build.ps1 -Target firmware -Python '<IDF Python.exe>'
```

输出：
- APK：`mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk`
- 官方系统更新包：`mosaicobot-firmware/build-mosaicobot/edge_agent-system-update.irisfw`
- 构建完整日志：固件目录 `.codex-runs/idf-low-noise-build/`

不复制旧 sdkconfig（可能含私人配置）；保留默认配置与板级源码。不要用默认 profile 构建或 fullclean 绕过兼容检查。

## 安装与官方刷机
先查看 `esp-mosaico-vibe-custom/AGENTS.md` 及 `esp-mosaico-vibe-custom/docs/mosaico-cli_CN.md`。所有设备更新必须走官方 `mosaico.py`，不直接 idf.py flash/esptool、不整机擦除。

```powershell
# 手机：从 adb devices -l 核对序列号，保留原应用数据。
./tools/install.ps1 -Target android -Serial '<ADB serial>'
# 已有正常固件/Vibe 的 Mosaico：显式选择实时身份，不使用历史 IP。
./tools/install.ps1 -Target firmware -DeviceId '<verified Device ID>' -Python '<Python.exe>'
```

设备脚本先查询归属、claim 和实时 device-status，保存 crash/log 证据，再执行 system-update。若其他工程占用设备，先协调，不能强制抢占。更新应验证同一 Device ID，normal → Vibe → normal 的 Boot ID 变化及 healthy/validated 状态；超时查询原 operation，不重新写入。

**全新/未验证设备**不等同于上述正常更新：按 Vibe 官方 `recover` 流程先完成 provisioning，必要时人工进入 ROM，再核硬件身份与 OTA 就绪状态；不得用整机 BIN 覆盖 NAND/NVS。脚本不自动替你进入 ROM 或恢复。

## 运行
启动手机 `dev.mosaicobot`，连接与设备相同的局域网并选择设备。可从应用中心获取内置 Lua 作品，不配置模型也能验收部署/启动/停止。AI 创作需在手机配置自己的模型端点和 Key；凭据保留手机本地，不进入仓库。

设备 Works 来自真实目录。预装只读，删除仅针对确认的单个手机包版本；运行中先停止，再删除并读回文件，手机源码与其他版本保留。

## 许可与发布状态
见 `THIRD_PARTY.md`。定制 Vibe 工具的部分上游源码尚未明确授予开源许可；提交者已确认公开发布权限；不因改名而自动获得新的开源许可。公开仓库： https://github.com/PlepleLiang/mosaicobot 。APK/固件构建产物不放入源码 Git，必要时单独发布附件。
