# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-user FastAPI service wrapping Zerodha's Kite Connect API to automate a specific trading flow: place a CNC buy order, wait for it to fill (via Kite's postback webhook, not polling), then place a GTT (Good Till Triggered) OCO order covering both a target and a stoploss exit in one shot.

## Commands

```bash
# Setup
python3 -m venv venv
venv/bin/pip install -r requirements.txt
# create .env with KITE_API_KEY and KITE_API_SECRET

# Local dev — starts uvicorn (--reload) AND the Cloudflare Tunnel together
./run.sh

# Or run the API alone, without the tunnel (postback won't be reachable from Kite)
venv/bin/uvicorn kite_app.main:app --reload
```

There is no test suite and no linter configured in this repo.

### Daily login

Kite access tokens expire daily. Visit `GET /login` in a browser once a day; it redirects through Kite's login page and `GET /callback` exchanges the resulting `request_token` for an access token, caching it to `.access_token`. Every trading endpoint reads that file fresh on each call via `kite_client.get_kite()`.

## Architecture

**Request flow**: `GET /login` → `GET /callback` (auth.py) → `.access_token` cached on disk → every other endpoint calls `kite_client.get_kite()` to get an authenticated client. `get_kite()` reuses a single module-level `KiteConnect` instance (for its underlying `requests.Session`/connection pool) but re-reads the access token from disk on *every* call, so a fresh login is picked up immediately without any in-memory state to keep in sync.

**Order lifecycle**: `POST /orders/buy` (kite_app/orders/orders.py) validates the request then delegates to `order_service.buyStock()`, which builds the `kite.place_order()` params (LIMIT orders pass `price`; everything else hardcodes `market_protection=-1`) and seeds `store.ORDER_STORE` with the returned `order_id`. The route returns immediately — it does not poll for fill status. Instead, Kite's Postback URL (configured in the Kite Connect developer console, not in code) hits `POST /postback` on every status change; the handler verifies a `SHA256(order_id + order_timestamp + API_SECRET)` checksum before trusting the payload, then writes into `store.ORDER_STORE`. `GET /orders/{id}` just reads that in-memory dict — it never calls Kite live. `ORDER_STORE` is intentionally ephemeral (lost on restart); only `.access_token` needs to survive.

**GTT exits**: `_compute_prices()` in orders.py computes target/stoploss prices as `entry_price * (1 ± pct/100)`, tick-rounded via `config.round_to_tick` (NSE tick size 0.05). `GET /orders/{id}/gtt-preview` shows the computed prices without placing anything; `POST /orders/{id}/gtt` places both legs as a single OCO GTT (`kite.place_gtt(trigger_type=kite.GTT_TYPE_OCO, ...)`) — whichever price hits first fires, the other auto-cancels. There's also a standalone `GET /gtt-preview?price=...&target_pct=...&sl_pct=...` that does the same math from an arbitrary price instead of a filled order's average price. Note: the app has no way to tell after the fact which leg (target or stoploss) actually fired — `place_gtt`'s response (`trigger_id`) is never persisted anywhere to correlate against later.

**Module layout**: `kite_app/orders/` is a Python namespace package (no `__init__.py`) — imported as `from kite_app.orders import orders` in `main.py`. `orders.py` holds the router; order-placement logic lives in `order_service.py` alongside it. `config.py` loads `.env` and holds constants (`API_KEY`, `API_SECRET`, `EXCHANGE_NSE`, `PRODUCT="CNC"`, `TICK_SIZE`). `schemas.py` holds all Pydantic request/response models, including the `OrderType` enum (`MARKET`, `LIMIT`, `AMO`, `DELIVERED`, `CANCELLED`). `store.py` is the in-memory order tracker described above.

**`kite_app_v2/`**: an early-stage, not-yet-wired-up rewrite living alongside `kite_app/` — a separate FastAPI app skeleton (`main.py`), plus stub files for a future `KiteSession`/client abstraction (`kiteSession.py`, `zerodhaclient.py`). Nothing imports from it and nothing runs it; treat it as scratch/in-progress rather than part of the live architecture until it's actually integrated.

**Networking**: Kite's Redirect URL and Postback URL are both configured externally in the Kite Connect developer console — they are not hardcoded, and must point wherever the app is actually reachable. Locally, the laptop has no public IP, so reachability comes from a Cloudflare Tunnel (`cloudflared tunnel run kite-postback`, hostname `postback.ayushgupta.us`, config at `~/.cloudflared/config.yml`, started by `run.sh`) — an outbound-only connection, no inbound port needed.

`market_protection` (required by Kite for MARKET orders) is hardcoded to `-1` (Kite's automatic protection) in `order_service.buyStock()`, regardless of what's in the request — the field is still present but commented out on `BuyOrderRequest` in schemas.py for possible future use.
