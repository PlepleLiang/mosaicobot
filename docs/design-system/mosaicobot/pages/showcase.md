# 参赛展示站 · Showcase

范围：仅 `docs/site/`，不覆盖手机 05 或设备 06 的既有设计规则。

## 设计方向与 skill

使用 `C:/Users/admin/.cursor/skills/ui-ux-pro-max/SKILL.md`，通过现有 `py -3` 执行设计系统检索及 `responsive accessible interactive demo --stack html-tailwind`。

- 初次 `programmable hardware playful product showcase` 推荐叙事型展示；采用清楚的故事顺序，不采用重滚动动画。
- 收窄 `hardware companion creative minimal` 后的 Minimalism & Swiss Style 适合实体工坊：网格编号、留白、高对比、清晰 CTA。
- 推荐金色 / 电商配色不匹配 Mosaico；沿用暖白、石墨、信号橙，不持久化不适用推荐。
- 使用原生 HTML / CSS / SVG / Canvas，不因 skill 的栈检索引入 Tailwind；字体沿用项目本地系统字体，不依赖在线素材。

## 呈现与交互

首屏把可操作的 Mosaico 放入手机背贴、掌机并列、独立挂件三种构图，同一屏幕状态随形态保留。背包与手机挂点可切换；外观是概念，不声明磁吸与量产。

创作台支持九种模板与有限参数；未知意图拒绝，不假装任意 AI 生成。草稿 → 保存不可变版本 → 模拟连接 → 用户确认部署 → 方屏运行 → 新版本修改。站点、本机 App 和旧原型数据互不同步。

主要按钮深底白字；选中悬停保持深底。控件至少 44px，方向键触点 44px；状态有文本，像素宠数值与反馈提供文字替代。模态确认不会因打开或取消就停止方屏；确认部署后焦点进入方屏。后台 / 失焦清零并暂停；方向控制支持触屏和键盘；短按开火使用输入边沿。

布局覆盖 375 / 768 / 1024 / 1440。减少动态效果设置禁用装饰动画，功能游戏仍由用户主动开始并可暂停。原交互链接保留完整评审场景。

## 检查入口

`docs/tests/showcase.cjs`：浏览器回归、布局、短触、版本与模态、持久化、反馈与焦点。截图写入系统临时目录，不进入仓库。发布产物由 `tools/build-pages.cjs` 白名单生成，不含设备历史证据或配置。
