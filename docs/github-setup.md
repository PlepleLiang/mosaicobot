# GitHub MCP 与发布

官方 GitHub MCP Server v2.0.2 Windows x86_64 已安装并用官方 SHA256 校验。安装器 `tools/install-github-mcp.py` 可重现下载，二进制保留本机用户工具目录，不进入源码仓库。

## 账号配置
本机 StepCode 已注册名为 `github` 的 stdio MCP：官方 `github-mcp-server.exe stdio --oauth-scopes repo,read:org --toolsets repos,users`。未硬编码 PAT，未启用请求/响应日志。

1. 重启 StepCode（`mcp add` 提示需要重启才加载新工具）。
2. 调用 GitHub 的账号查询工具，按弹出的浏览器 OAuth 提示登录**自己的 GitHub 账号**并确认授权。
3. 不把 Token、密码、设备验证码或私钥发到对话。官方原生版使用内置 OAuth 应用与本机回调；令牌只在 MCP 进程内存中保留，重启后可能需要重新授权。
4. 先只读确认 GitHub 用户名，再在这个账户创建名为 `mosaicobot` 的 Public 仓库。存在同名仓库时不得覆盖。

该 MCP 的浏览器授权与 Git 命令 push 的凭据是两条链路。批量源码上传使用 Git；如 Git Credential Manager 弹出浏览器，仍由用户在本机授权，不导出 MCP 令牌或写入 remote URL。

## 上传范围与门槛
只提交本目录：固件、Android、定制 Vibe/工具、相关资源及文档，以及固定版本的官方依赖 gitlink。不提交旧构建输出、虚拟环境、设备状态、模型配置、签名文件和原始 crash dump。构建产物可通过 Release 附件另行交付。

候选文本已做一次高置信 GitHub Token/私钥模式检查，未发现命中；这不是对所有秘密/图片隐私的完整安全保证。历史截图、日志、其他格式和第三方素材仍需审查。

`THIRD_PARTY.md` 记录当前公开发布许可门槛。未经权利方确认的源码不能因用户选择 Public 自动获得开源许可。完成本地工程不等于已公开上传；GitHub URL 仅在实际创建并上传成功后交付。

官方参考：
- https://github.com/github/github-mcp-server
- https://github.com/github/github-mcp-server/blob/main/docs/oauth-login.md
- https://github.com/github/github-mcp-server/releases/tag/v2.0.2
