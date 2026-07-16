#!/data/data/com.termux/files/usr/bin/sh
set -eu

PORT="${1:-4500}"
TOKEN_FILE="${CODEX_APP_SERVER_TOKEN_FILE:-$HOME/.codex/app-server-token}"
WEB_SEARCH_MODE="${CODEX_APP_SERVER_WEB_SEARCH:-disabled}"
WAKE_LOCKED=0

case "$WEB_SEARCH_MODE" in
    disabled|cached|indexed|live) ;;
    *)
        echo "无效的网页搜索模式：$WEB_SEARCH_MODE（可选：disabled、cached、indexed、live）" >&2
        exit 2
        ;;
esac

if ! command -v codex >/dev/null 2>&1; then
    echo "未找到 codex，请先在 Termux 中安装 Codex CLI。" >&2
    exit 1
fi

if command -v termux-wake-lock >/dev/null 2>&1; then
    termux-wake-lock && WAKE_LOCKED=1
fi

cleanup() {
    if [ "$WAKE_LOCKED" -eq 1 ] && command -v termux-wake-unlock >/dev/null 2>&1; then
        termux-wake-unlock || true
    fi
}
trap cleanup EXIT INT TERM

if [ ! -s "$TOKEN_FILE" ]; then
    mkdir -p "$(dirname "$TOKEN_FILE")"
    umask 077
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32 > "$TOKEN_FILE"
    else
        od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$TOKEN_FILE"
    fi
    chmod 600 "$TOKEN_FILE"
fi

TOKEN="$(tr -d '\r\n' < "$TOKEN_FILE")"

echo "Codex Android App Server: ws://127.0.0.1:${PORT}"
echo "App Server 传输 Token: ${TOKEN}"
echo "网页搜索模式: ${WEB_SEARCH_MODE}"
echo "保持此 Termux 会话运行，然后打开 Android 客户端。"
set +e
codex app-server \
    -c "web_search=\"${WEB_SEARCH_MODE}\"" \
    --listen "ws://127.0.0.1:${PORT}" \
    --ws-auth capability-token \
    --ws-token-file "$TOKEN_FILE"
STATUS=$?
set -e
exit "$STATUS"
