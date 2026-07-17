# Codex Android 客户端

一个使用 Jetpack Compose 与 Material Design 3 编写的原生 Android 客户端。它通过 Codex 官方 `app-server` JSON-RPC 协议，连接同一台 Android 设备上 Termux 中已经登录的 Codex CLI。

## 功能

- Material Design 3、自适应浅色/深色与动态配色
- 新建、查看、恢复并永久删除本地 Codex 会话
- 流式显示回复、计划、命令执行、文件修改和工具状态
- 命令审批与文件 Diff 审阅
- 审批队列、服务端自动解决同步和 MCP elicitation 表单/链接
- 中断正在运行的任务
- 从 App Server 获取模型、Codex 有效配置中的默认思考深度与 Skills，并在设置对话框中选择
- 通过 App Server 浏览 Termux 文件系统并选择真实工作目录
- 运行任务时使用前台服务保持连接，后台审批或用户输入时发送系统通知
- 自定义 App Server 地址和 Bearer Token
- 会话通知隔离、断线自动恢复、历史分页和流式消息批处理
- Markwon/CommonMark Markdown 渲染、表格、任务列表与安全外链
- OkHttp WebSocket 心跳、请求超时和只读请求过载重试

## 在 Termux 中启动 Codex

先确认 Codex 已安装并完成登录：

```sh
codex --version
codex login
```

推荐使用项目附带的安全启动脚本。脚本会生成权限为 `600` 的随机 capability token、启用 WebSocket 鉴权，并在退出时释放 Wake Lock：

```sh
cd CodexAndroid
chmod +x termux/start-codex-server.sh
./termux/start-codex-server.sh
```

启动脚本不会覆盖 Codex 的网页搜索设置。使用只兼容 Responses API、但没有 `/alpha/search` 路由的第三方 Provider 时，应让 Codex 使用 Responses API 的托管网页搜索工具：

```toml
model_provider = "proxy"
web_search = "live"

[model_providers.proxy]
name = "Responses Proxy"
base_url = "https://your-provider.example/v1"
wire_api = "responses"
requires_openai_auth = true
```

Provider 的 `name` 不要设置成 `OpenAI`；当前 Codex 会把该名称视为 OpenAI Provider 并启用独立的 `/alpha/search` 客户端。代理服务还必须在 Responses API 中支持 `type = "web_search"`。

把脚本输出的“App Server 传输 Token”填入客户端设置。这个 Token 只用于 Android 客户端到 App Server 的传输鉴权，不是 Codex 登录凭证、OpenAI API Key 或 Codex Access Token。

客户端默认连接 `ws://127.0.0.1:4500`，默认工作目录为 `/data/data/com.termux/files/home`。工作目录必须是 Termux 中真实存在的绝对路径，可在应用的“连接与工作区”中改为具体项目目录。

## 构建

在 Termux 中执行：

```sh
cd CodexAndroid
./gradlew assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安全提示

默认配置只允许 `ws://` 连接回环地址；非本机地址会被客户端强制要求使用 `wss://`。即使在 Android 本机也建议始终启用 capability token，因为其他安装了网络权限的应用也可能访问设备回环端口。Token 使用 Android Keystore 加密保存且不会进入系统备份。

当前 Codex App Server WebSocket 传输仍是实验性接口，CLI 升级后协议可能变化。协议方法集中维护在 `CodexProtocol.kt`，审批决策优先读取服务端运行时提供的 `availableDecisions`。

升级 Codex CLI 后，可在项目目录执行 `./termux/verify-codex-protocol.sh`，检查当前 CLI schema 是否出现尚未适配的服务端请求。

Android 构建使用 `compileSdk/targetSdk 36`、Android Gradle Plugin 8.13.2、Gradle 8.14.5 与 Kotlin 2.3.0。连接状态会显示 App Server 返回的实际 CLI User-Agent，不再依赖固定版本号判断兼容性。
