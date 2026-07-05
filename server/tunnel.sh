#!/usr/bin/env bash
# Beanshelf server + Cloudflare tunnel helper.
#
#   ./tunnel.sh quick   → start the server + a throwaway https://*.trycloudflare.com
#                         tunnel (no domain/login needed; URL changes each run).
#   ./tunnel.sh url     → print the current quick-tunnel URL.
#   ./tunnel.sh stop    → stop the tunnel and the server.
#
# For a PERMANENT url tied to your own domain, see named-tunnel steps in README.
set -euo pipefail
cd "$(dirname "$0")"

SERVER_LOG=/tmp/beanshelf-server.log
TUNNEL_LOG=/tmp/cloudflared-quick.log

start_server() {
  if curl -sf http://127.0.0.1:8787/ping >/dev/null 2>&1; then
    echo "server already up on :8787"
  else
    echo "starting server on :8787…"
    nohup python3 -m uvicorn main:app --host 0.0.0.0 --port 8787 > "$SERVER_LOG" 2>&1 &
    sleep 3
    curl -sf http://127.0.0.1:8787/ping >/dev/null && echo "server up"
  fi
}

case "${1:-quick}" in
  quick)
    start_server
    pkill -f "cloudflared tunnel --url" 2>/dev/null || true
    sleep 1
    echo "starting cloudflare quick tunnel…"
    nohup cloudflared tunnel --url http://127.0.0.1:8787 > "$TUNNEL_LOG" 2>&1 &
    for _ in $(seq 1 15); do
      URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -1 || true)
      [ -n "$URL" ] && break
      sleep 1
    done
    echo "public URL: ${URL:-<not ready — check $TUNNEL_LOG>}"
    echo "put this in the app's Server field, or rebuild DEFAULT_SERVER in SocialScreen.kt"
    ;;
  url)
    grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -1
    ;;
  stop)
    pkill -f "cloudflared tunnel --url" 2>/dev/null && echo "tunnel stopped" || echo "no tunnel"
    pkill -f "uvicorn main:app" 2>/dev/null && echo "server stopped" || echo "no server"
    ;;
  *)
    echo "usage: ./tunnel.sh [quick|url|stop]"; exit 1 ;;
esac
