import os

from fastapi import HTTPException
from kiteconnect import KiteConnect

from kite_app.config import API_KEY, API_SECRET, TOKEN_FILE

_kite: KiteConnect = None


def get_kite() -> KiteConnect:
    """Return a shared KiteConnect client with the current cached access token.

    Reuses one client (so its underlying requests.Session/connection pool
    persists across calls) but re-reads .access_token from disk on every
    call, so a fresh login is always picked up immediately.

    Raises HTTPException(401) if we're not logged in yet — caller should
    hit GET /login first.
    """
    global _kite

    if not API_KEY or not API_SECRET:
        raise RuntimeError("KITE_API_KEY / KITE_API_SECRET not found. Create a .env file.")

    if _kite is None:
        _kite = KiteConnect(api_key=API_KEY)

    if not os.path.exists(TOKEN_FILE):
        raise HTTPException(status_code=401, detail="Not logged in. Visit /login first.")

    with open(TOKEN_FILE) as f:
        access_token = f.read().strip()

    if not access_token:
        raise HTTPException(status_code=401, detail="Not logged in. Visit /login first.")

    _kite.set_access_token(access_token)
    return _kite


def cache_access_token(access_token: str) -> None:
    # print(f"{access_token} - new one")
    with open(TOKEN_FILE, "w") as f:
        f.write(access_token)
