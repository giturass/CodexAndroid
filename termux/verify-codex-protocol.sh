#!/data/data/com.termux/files/usr/bin/sh
set -eu

if ! command -v codex >/dev/null 2>&1; then
    echo "未找到 codex。" >&2
    exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
    echo "未找到 jq，请在 Termux 中执行 pkg install jq。" >&2
    exit 1
fi

SCHEMA_DIR="$(mktemp -d "$HOME/tmp/codex-protocol.XXXXXX")"
trap 'rm -rf "$SCHEMA_DIR"' EXIT INT TERM
codex app-server generate-json-schema --experimental --out "$SCHEMA_DIR" >/dev/null

KNOWN_FILE="$SCHEMA_DIR/known.txt"
SERVER_FILE="$SCHEMA_DIR/server.txt"
sed -n '/object ServerRequest/,/^    }/p' \
    app/src/main/java/com/termuxcodex/client/data/CodexProtocol.kt |
    sed -n 's/.*= "\([^"]*\)".*/\1/p' | sort -u > "$KNOWN_FILE"
jq -r '.. | objects | select(has("method")) | .method.enum[]? // empty' \
    "$SCHEMA_DIR/ServerRequest.json" | sort -u > "$SERVER_FILE"

UNKNOWN="$(comm -23 "$SERVER_FILE" "$KNOWN_FILE")"
if [ -n "$UNKNOWN" ]; then
    echo "发现尚未纳入协议目录的 ServerRequest：" >&2
    echo "$UNKNOWN" >&2
    exit 2
fi

echo "Codex App Server 请求目录与当前 CLI schema 一致。"
echo "CLI：$(codex --version)"
