#!/usr/bin/env python3
"""
port80_forwarder.py — tiny helper that listens on port 80, the fixed
redirect target Kite sends the browser to after login for this app
(http://127.0.0.1/?request_token=...). It does nothing but forward that
query string to the real app's GET /callback on port 8000 and relay the
response back to the browser.

Only this ~30-line script needs root (ports <1024 are privileged on
macOS) — kite_app itself keeps running unprivileged on port 8000.

Usage (leave running in the background, once):
    sudo venv/bin/python port80_forwarder.py
"""

import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

TARGET = "http://127.0.0.1:8000/callback"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        query = self.path.split("?", 1)[1] if "?" in self.path else ""
        url = f"{TARGET}?{query}"
        try:
            with urllib.request.urlopen(url) as resp:
                body = resp.read()
                status = resp.status
        except urllib.error.HTTPError as e:
            body = e.read()
            status = e.code

        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        print(f"[port80_forwarder] {self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 80), Handler)
    print(f"Listening on http://127.0.0.1:80, forwarding to {TARGET}")
    server.serve_forever()
