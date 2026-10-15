# 第三方来源与许可边界

本工程保留原版权与许可证，不使用一个顶层许可证重新授权全部源码。

| 内容 | 上游来源与固定版本 | 集成方式 |
|---|---|---|
| 设备固件基线 | https://github.com/esp-mosaico/esp-mosaico-claw | 本地定制源码；原工程许可证保留 |
| esp-claw | https://github.com/espressif/esp-claw @ c4d5648f4d4447fb7f46679eb61e32a7ea253ce2 | 干净官方依赖 |
| Vibe 基线 | https://github.com/esp-mosaico/esp-mosaico-vibe @ 95469e63b621fc6ece3b0e313af8c1ba59dcd066 | 主仓库源码与本地修改纳入 esp-mosaico-vibe-custom |
| utils 基线 | https://github.com/esp-mosaico/esp-mosaico-utils @ fc63d43f3ac72aa54e701b07b07fbed07a16b3ca | 实际修改源码直接纳入，不能用未改官方版本代替 |
| BSP | https://github.com/esp-mosaico/esp-mosaico-bsp @ a2c985ad698b3f04a21c63e006b43e7e948bc1d7 | 干净官方依赖 |
| Raylib Lite Engine | https://github.com/espressif2022/raylib-lite-engine @ ca3200a6b7f63e2bdea07851e19fdbd3a42eb742 | 干净官方依赖 |

实际基线及修改路径见 `tools/source-snapshot.json`。ESP-IDF/组件注册表/Python/Gradle 下载的依赖继续遵守各自许可，不随本工程改名重新授权。字体、图像、音频及品牌素材仍需按各自 LICENSE/NOTICE/来源说明使用。

## 公开发布门槛
`esp-mosaico-vibe-custom/submodule/esp-mosaico-utils/LICENSES.md` 明确记录：ESP-Iris 组件为 Apache-2.0，但迁移的部分 Recovery 与 host-tool 源码没有仓库级许可证；维护方应在对第三方发布前确定许可。当前不能自行把这些文件声明为 MIT/Apache-2.0，也不能通过改目录名消除这一边界。

2026-10-15，项目提交者明确确认已获得本地定制 Recovery/host 工具及内部参赛文档、素材的公开发布权限。该确认不等同于为没有明确许可证的上游文件新增 MIT/Apache-2.0 授权；继续保留原版权和许可说明，使用或再分发时须核实适用权利。

历史 crash/core dump 是设备内存证据，可能包含敏感运行时数据，不纳入公开 Git。原 Git 历史、遗漏或旧版本文件以及运行时证据已保存在本机 `.migration-backup/`（Git 忽略），不随源码上传。历史截图中的身份、地址仅作为当时的验收记录。
