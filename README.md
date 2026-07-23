# Codex Android 客户端

一个使用 Jetpack Compose 与 Material Design 3 编写的原生 Android 客户端。它通过 Codex 官方 `app-server` JSON-RPC 协议，连接同一台 Android 设备上 Termux 中已经登录的 Codex CLI。

## 功能

- Material Design 3、自适应浅色/深色与动态配色
- 跟随系统、浅色和深色三种主题模式
- 新建、查看、恢复并永久删除本地 Codex 会话
- 流式显示回复、计划、命令执行、文件修改和工具状态
- 命令审批与文件 Diff 审阅
- 运行中通过 `turn/steer` 追加指令，并可随时停止当前任务
- 多行圆角任务输入栏、图片附件、`/` 命令、`@` 文件引用与 `$` Skill 引用
- 输入栏快捷选择模型和思考深度
- java-diff-utils 解析的上下合并 Diff 视图、工具执行时间线与增删行统计
- 会话重命名和本地置顶
- 平板与大屏横向双栏布局
- 审批队列、服务端自动解决同步和 MCP elicitation 表单/链接
- 中断正在运行的任务
- 从 App Server 获取模型、Codex 有效配置中的默认思考深度与 Skills，并在设置对话框中选择
- 通过 App Server 浏览 Termux 文件系统并选择真实工作目录
- 运行任务时使用前台服务保持连接，后台审批或用户输入时发送系统通知
- 自定义 App Server 地址和 Bearer Token
- 通过 App Server [`thread/list`](https://learn.chatgpt.com/docs/app-server#list-threads-with-pagination--filters) 的 `searchTerm` 参数搜索已保存会话、MCP 服务状态
- 会话通知隔离、断线自动恢复、历史分页和流式消息批处理
- Markwon/CommonMark Markdown 渲染、表格、任务列表与安全外链
- OkHttp WebSocket 心跳、请求超时和只读请求过载重试

## 在 Termux 中启动 Codex

先确认 Codex 已安装并完成登录：

```sh
codex --version
codex login
```

按照 OpenAI 官方 Codex CLI 文档，直接启动本地监听：

```sh
codex app-server --listen ws://127.0.0.1:4500
```

需要保持设备唤醒时，可在启动前单独运行 `termux-wake-lock`。如果 Provider 使用 Responses API 并支持托管网页搜索，可按官方配置参考启用 Responses wire API 和实时网页搜索：

```toml
model_provider = "proxy"
web_search = "live"

[model_providers.proxy]
name = "Responses Proxy"
base_url = "https://your-provider.example/v1"
wire_api = "responses"
env_key = "PROXY_API_KEY"
```

`env_key` 用于从环境变量读取该 Provider 自己的 API key。只有代理明确要求使用 ChatGPT/OpenAI 鉴权时才配置 `requires_openai_auth = true`；官方说明启用该选项后会忽略 `env_key`。Provider 的具体能力和鉴权方式应以其服务文档为准。

如需 WebSocket 鉴权，请使用 Codex CLI 官方的 `--ws-auth capability-token --ws-token-file /absolute/path` 参数，并把同一 Token 填入客户端设置。这个 Token 只用于 Android 客户端到 Codex CLI 的传输鉴权，不是 Codex 登录凭证、OpenAI API Key 或 Codex Access Token。

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

当前 Codex App Server WebSocket 传输仍是实验性接口，CLI 升级后协议可能变化。项目只使用 OpenAI 官方文档公开的稳定字段；协议方法集中维护在 `CodexProtocol.kt`，审批决策优先读取服务端运行时提供的 `availableDecisions`。

Android 构建使用 `compileSdk/targetSdk 36`、Android Gradle Plugin 8.13.2、Gradle 8.14.5 与 Kotlin 2.3.0。连接状态会显示 App Server 返回的实际 CLI User-Agent，不再依赖固定版本号判断兼容性。

## GitHub Actions 签名发布

推送到 `main` 分支后，GitHub Actions 会运行测试、构建签名 Release APK，并更新标签和 Release `latest`。也可以在 Actions 页面手动运行该工作流。Release 中的 APK 文件名固定为 `CodexAndroid-latest.apk`。

请先在仓库的 **Settings → Secrets and variables → Actions** 中配置以下 Repository secrets：

- `ANDROID_KEYSTORE_BASE64`：签名 KeyStore 文件的 Base64 内容。可使用 `base64 -w 0 release.jks` 生成；Termux 可使用 `base64 release.jks | tr -d '\n'`。
- `ANDROID_KEYSTORE_PASSWORD`：KeyStore 密码。
- `ANDROID_KEY_ALIAS`：签名密钥别名。
- `ANDROID_KEY_PASSWORD`：签名密钥密码。

签名密钥及密码不会写入仓库。请妥善备份 KeyStore；丢失后将无法用同一签名升级已经发布的 APK。
