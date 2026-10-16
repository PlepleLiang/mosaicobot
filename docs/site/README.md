# MosaicoBot 展示站

`docs/site/index.html` 是面向参赛评审的产品展示与可玩体验，原交互文档仍在 `docs/index.html`。

## 本地预览与验证

无需构建或依赖，直接打开 index.html。也可从仓库根目录启动 `py -3 -m http.server 8080`，访问 `http://localhost:8080/docs/site/`。

生成发布产物（输出目录必须尚不存在）：

```bash
node tools/build-pages.cjs /path/to/new-output
```

在该目录启动静态服务即可验证发布版。打包将原型放到 `prototype/`，展示站为根首页；所有站内路径为相对路径，兼容 GitHub Pages `/mosaicobot/` 子路径。使用白名单，不发布验证日志、设备 XML、APK、固件或私密配置。

浏览器测试：需要本机 Playwright 或 playwright-core 及 Edge / Chromium。测试依赖无需进入网站产物。

```bash
node --check docs/site/app.js
node --check docs/site/scenes.js
# 环境变量 PLAYWRIGHT_PATH 可指定本机已安装模块；BROWSER_PATH 可指定浏览器
node docs/tests/showcase.cjs
# 仓库静态服务；发布产物服务则传根 URL
node docs/tests/showcase.cjs http://localhost:8080/docs/site/
```

## GitHub Pages

1. 仓库 Settings → Pages → Build and deployment → Source 选择 **GitHub Actions**。
2. 将本次站点、打包脚本与 `.github/workflows/pages.yml` 提交到 main。
3. 在 Actions 查看 `Deploy MosaicoBot experience` 成功；也可手动运行 workflow_dispatch。
4. 目标地址：`https://plepleliang.github.io/mosaicobot/`。只有部署任务和线上访问成功后，此地址才是有效交付链接。

## 演示边界

- 九种本地模板，浏览器独立绘制与运行，不执行 Lua、不调用真实 AI 或硬件接口。
- 创作台默认可用，没有模拟连接、失败或断开流程。生成九种模板和有效参数编辑会立即启动右栏独立草稿试玩；保存、打开和修改版本也可试玩，放弃后显示空状态。
- 草稿试玩不等于保存或部署。版本保存于当前浏览器，每次部署均需确认，确认前不替换上方展台；确认后保持创作区可见。
- 每个保存版本可单独删除，原生确认弹窗提供取消 / Escape。只删除选中版本并持久化；删除当前打开的保存版本会清空草稿试玩并禁用再次部署，但上方正在运行的快照保留。其他未保存草稿不受影响；删除后焦点回到邻近版本按钮，空列表回到标题。存储失败会明确告知删除仅在本次会话生效。
- 两处方屏使用独立 Screen，形态与输入互不串扰；支持完整开始 / 暂停 / 重试 / 退出、触屏 / 键盘及画板交互。失焦、隐藏页面、对话框和形态 / 编辑焦点切换释放输入。
- 设备背贴、方屏在上 / 横屏手机手柄在下、手机 / 背包挂件为形态概念，不表示磁吸或附件量产。默认背贴概念采用正面手机与独立方屏并列，避免把方屏缩小成附件。
- 两处设备共享原型比例：竖屏手机 390:834、横屏手机 834:390，竖屏宽为方屏边长的 0.8125，方屏边长为横屏手机宽的 480/834。手机圆角 / 框内留白按原型 43/7px 缩放；Mosaico 保持浅色 2.25% 等厚边框、12% 外圆角、10% 内圆角。横屏控制器沿用 `docs/game-mode.css` 的深色配色，窄屏采用 2×2 方向区和独立 A 键，每个触点 44×44px，状态与标题在中央列，方屏与手机不重叠。
- 九种场景复用现有玩法状态，只升级 Canvas 表现：原型 `A LITTLE FOCUS` 轨道倒计时、霓虹飞行 / 赛车 / 坦克、发光蛇身、分层像素宠、签名字阵、表情光环与光点网格。系统减少动态效果时冻结环境运动，游戏和真实倒计时照常运行。作品集使用实际 Canvas 静态缩略图，不增加动画循环。
- 三个精选灵感按钮只填写既有模板接受的输入（蓝色慢速飞机、绿色专注 5 分钟、粉色像素宠），不是新模型能力。可选真实 AI / Lua / 设备集成需要后端和授权；本次不实现模拟接入或凭据 UI，不读取密钥，不发起 AI 网络请求。
- 本次只使用 Edge 页面截图供视觉审批，不运行测试 / 类型检查 / lint 套件；截图视口为 1440px 和 375px。
- 专注使用真实网页倒计时，隐藏页面暂停；像素宠没有后台养成；画板退出 / 切换会重置。
- 原型内系统、连接、Works、中心上传和手柄均为独立模拟状态，不与本站、Android 或真实设备同步。
