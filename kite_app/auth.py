from typing import Optional

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import RedirectResponse
from kiteconnect import KiteConnect

from kite_app.config import API_KEY, API_SECRET
from kite_app.kite_client import cache_access_token

router = APIRouter()

def checkKeysExists():
    if not API_KEY or not API_SECRET:
        raise HTTPException(status_code=500, detail="KITE_API_KEY / KITE_API_SECRET not found. Create a .env file.")

@router.get("/login")
def login() -> RedirectResponse:
    checkKeysExists()
    kite = KiteConnect(api_key=API_KEY)
    return RedirectResponse(kite.login_url())


@router.get("/callback")
def callback(request_token: str = Query(...), status: Optional[str] = Query(None)) -> dict:
    print(f"[callback] request_token = {request_token}")

    if status is not None and status != "success":
        raise HTTPException(status_code=400, detail=f"Kite login did not succeed (status={status}).")

    kite = KiteConnect(api_key=API_KEY)
    try:
        session = kite.generate_session(request_token, api_secret=API_SECRET)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Login failed: {e}")
    
    print(f"{session} - new one")

    cache_access_token(session["access_token"])
    print("[callback] logged in, access token cached.")
    return {"status": "ok", "message": "Logged in, access token cached."}
