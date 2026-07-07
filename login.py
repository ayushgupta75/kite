#!/usr/bin/env python3
"""
login.py — one-time-per-day Kite login helper.

Kite's redirect for this app is fixed to http://127.0.0.1/ (a page that
fails to load, by design). Run this script, open the printed URL, log in,
then paste back the URL your browser lands on — this exchanges it for an
access token and caches it to .access_token, which kite_app/main.py then
uses for every trading request.

Usage: python login.py
"""

from urllib.parse import parse_qs, urlparse

from kiteconnect import KiteConnect

from kite_app.config import API_KEY, API_SECRET
from kite_app.kite_client import cache_access_token


def main() -> None:
    if not API_KEY or not API_SECRET:
        raise SystemExit("KITE_API_KEY / KITE_API_SECRET not found. Create a .env file.")

    kite = KiteConnect(api_key=API_KEY)

    print("\n1. Open this URL in your browser and log in:\n")
    print(f"   {kite.login_url()}\n")
    print("2. After login, your browser will redirect to a URL starting with")
    print("   http://127.0.0.1/?request_token=...  (the page itself will fail to load — that's fine).")
    print("3. Copy that FULL URL from your browser's address bar and paste it below.\n")

    pasted = input("Paste the redirect URL here: ").strip()

    if pasted.startswith("http"):
        query = parse_qs(urlparse(pasted).query)
        request_token = query.get("request_token", [None])[0]
    else:
        request_token = pasted

    if not request_token:
        raise SystemExit("Could not find request_token in what you pasted.")

    session = kite.generate_session(request_token, api_secret=API_SECRET)
    cache_access_token(session["access_token"])
    print("\n[OK] Logged in, access token cached to .access_token.\n")


if __name__ == "__main__":
    main()
