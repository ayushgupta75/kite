#!/usr/bin/env bash
# Starts the trading API on port 8000 and the Cloudflare tunnel that
# exposes /postback to Kite (postback.ayushgupta.us -> localhost:8000).
# Ctrl-C stops both. No sudo needed.
set -euo pipefail

trap 'kill "$SERVER_PID" "$TUNNEL_PID" 2>/dev/null' EXIT

cloudflared tunnel run kite-postback &
TUNNEL_PID=$!

venv/bin/uvicorn kite_app.main:app --reload &
SERVER_PID=$!

wait -n
