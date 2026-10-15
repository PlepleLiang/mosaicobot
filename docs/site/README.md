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
# 环境变量 PLAYWRIGHT_PATH 可指定本机已安装模块；PLAYWRIGHT_BROWSER_PATH 可指定浏览器
node docs/tests/showcase.cjs http://localhost:8080/
```

## GitHub Pages

1. 仓库 Settings → Pages → Build and deployment → Source 选择 **GitHub Actions**。
2. 将本次站点、打包脚本与 `.github/workflows/pages.yml` 提交到 main。
3. 在 Actions 查看 `Deploy MosaicoBot experience` 成功；也可手动运行 workflow_dispatch。
4. 目标地址：`https://plepleliang.github.io/mosaicobot/`。只有部署任务和线上访问成功后，此地址才是有效交付链接。

## 演示边界

- 九种本地模板，浏览器独立绘制与运行，不执行 Lua、不调用真实 AI 或硬件接口。
- 颜色 / 游戏速度 / 专注分钟 / 签名可修改，版本保存于当前浏览器，首次部署需要确认。
- 设备背贴、手机手柄并列方屏、手机 / 背包挂件为形态概念，不表示磁吸或附件量产。
- 专注使用真实网页倒计时，隐藏页面暂停；像素宠没有后台养成；画板退出 / 切换会重置。
- 原型内系统、连接、Works、中心上传和手柄均为独立模拟状态，不与本站、Android 或真实设备同步。
