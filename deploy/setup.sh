#!/usr/bin/env bash
# One-time EC2 bootstrap for the Kite Trading API. Run this on a fresh
# instance after cloning the repo to /home/ubuntu/kite.
#
# Assumes: Ubuntu AMI, repo already cloned, .env already scp'd over
# (this script does NOT create secrets for you).
#
# Usage: ./deploy/setup.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Installing system packages"
sudo apt-get update -y
sudo apt-get install -y python3-venv curl

echo "==> Creating venv and installing dependencies"
python3 -m venv venv
venv/bin/pip install --upgrade pip
venv/bin/pip install -r requirements.txt

if [ ! -f .env ]; then
  echo "WARNING: .env not found. scp it over before starting the service:"
  echo "  scp .env ubuntu@<ec2-host>:/home/ubuntu/kite/.env"
fi

echo "==> Installing cloudflared"
if ! command -v cloudflared &> /dev/null; then
  curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
  sudo dpkg -i cloudflared.deb
  rm cloudflared.deb
fi

echo "==> Cloudflare tunnel setup (interactive)"
echo "If you haven't already, run these manually (each needs browser auth or DNS API access):"
echo "  cloudflared tunnel login"
echo "  cloudflared tunnel create kite-api-prod"
echo "  cloudflared tunnel route dns kite-api-prod api.ayushgupta.us"
echo "Then copy deploy/cloudflared-config.yml.example to ~/.cloudflared/config.yml,"
echo "filling in the tunnel id, and run: sudo cloudflared service install"

echo "==> Installing systemd service for the API"
sudo cp deploy/kite-api.service /etc/systemd/system/kite-api.service
sudo systemctl daemon-reload
sudo systemctl enable kite-api
sudo systemctl restart kite-api

echo "==> Done. Check status with:"
echo "  sudo systemctl status kite-api"
echo "  sudo systemctl status cloudflared"
