# 新目录实机交付（2026-10-15）

- 新目录 APK `mosaicobot-android/app/build/outputs/apk/debug/app-debug.apk`：指定手机 ZYPZ261A01M66A000UC，`adb install -r` 返回 Success，手机数据保留。
- 新目录 Vibe CLI 与独立 `.tools/iris-host` 完成官方 system-update，包为新目录 `edge_agent-system-update.irisfw`；operation `dbb7573a-678f-4ce5-a807-408fa2c2f05c`，healthy/validated 均 true。
- 同一设备 `4553502d49524953010030eda0f45ede`，normal Boot `5725226456212278855` → Vibe `8228541063969100088` → normal `11327166519853508534`。
- 实际与预期 ELF SHA 均 `fd44692b3c4e0fffcb94e030afc56eaf32f2659e8559d6d878fd3461660adc55`。bootloader/layout 未改变；更新包不写 NAND/NVS。
- 手机新应用正常启动、LAN 在线、保留 8 个手机作品；设备应用真实计数 31。原 20 个 Works ID 集合与安装前完全一致，预装 Album/Dino 的不可删除行经 UI 检查。
- 通过手机运行本地“一刻专注 v1”，真实目录确认测试包 running；官方设备截图 `validation/new-directory-device-running.png` 已直接查看，画面正常。手机停止后读回 stopped。
- 通过手机 Works 列表确认仅删测试包 `mb_ba789cb2e9bd4d8d_v1_934f61d9`；异步处理后确认其从清单及 DATA `/apps` 文件消失，原 20 个 ID 全保留。手机源码未删除。
- 最终 device-status：同一新 normal Boot、stale false、crash_count 0、idle、无 queued/running jobs。

证据清单：`validation/new-directory-inventory.json`、`validation/new-directory-after-acceptance.json` 与设备截图。详细更新/health日志位于新 Vibe `.codex-runs/mosaico/`（私有，不进入 Git）；更新前有效历史 core 已由官方归档到本机项目会话目录，没有清除。

本次确认新目录产物可安装、更新并运行核心 Works；不声称弱网、断连竞态、长时稳定性或完整启动器/手柄回归通过。
