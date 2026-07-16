# Codex Android 客户端

一个使用 Jetpack Compose 与 Material Design 3 编写的原生 Android 客户端。它通过 Codex 官方 `app-server` JSON-RPC 协议，连接同一台 Android 设备上 Termux 中已经登录的 Codex CLI。

## 功能

- Material Design 3、自适应浅色/深色与动态配色
- 新建、查看、恢复并永久删除本地 Codex 会话
- 流式显示回复、计划、命令执行、文件修改和工具状态
- 命令与文件修改审批
- 审批队列、服务端自动解决同步和 MCP elicitation 表单/链接
- 中断正在运行的任务
- 从 App Server 获取模型、Codex 有效配置中的默认思考深度与 Skills，并在设置对话框中选择
- 通过 App Server 浏览 Termux 文件系统并选择真实工作目录
- 运行任务时使用前台服务保持连接，后台审批或用户输入时发送系统通知
- 自定义 App Server 地址和 Bearer Token
- 会话通知隔离、断线自动恢复、历史分页和流式消息批处理
- OkHttp WebSocket 心跳、请求超时和服务端过载指数退避

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

为兼容未实现 Codex 搜索接口的第三方 OpenAI-compatible Provider，启动脚本默认设置 `web_search = "disabled"`，避免 `/v1/alpha/search` 返回 404。如果当前 Provider 支持网页搜索，可显式启用：

```sh
CODEX_APP_SERVER_WEB_SEARCH=live ./termux/start-codex-server.sh
```

可选模式为 `disabled`、`cached`、`indexed` 和 `live`。手动启动 App Server 时，可添加 `-c 'web_search="disabled"'` 获得相同效果。

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

当前 Codex App Server WebSocket 传输仍是实验性接口，CLI 升级后协议可能变化。本项目按本机 `codex-cli 0.144.1` 生成的协议实现核心稳定流程。

Android 构建使用 `compileSdk/targetSdk 36`、Android Gradle Plugin 8.13.2、Gradle 8.14.5 与 Kotlin 2.3.0。协议客户端会在检测到未经验证的 Codex CLI 版本时显示兼容性提示。
