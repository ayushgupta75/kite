#!/usr/bin/env bash
# Pulls the latest code and restarts the service. Run on the EC2 box for
# every deploy after the initial ./deploy/setup.sh.
#
# Usage: ./deploy/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Pulling latest code"
git pull

echo "==> Installing any new dependencies"
venv/bin/pip install -r requirements.txt

echo "==> Restarting service"
sudo systemctl restart kite-api

echo "==> Done. Tailing logs (ctrl-c to stop watching, service keeps running):"
sudo journalctl -u kite-api -f -n 20
