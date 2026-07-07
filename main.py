#!/usr/bin/env python3
"""
trade.py — Manual Kite Connect trading script

Flow:
  1. You log in once (browser), paste back the redirect URL.
  2. Script places a CNC buy order.
  3. Polls until the order is filled, reads the actual average price.
  4. Computes target + stoploss prices from your % inputs.
  5. Shows you the numbers, asks for confirmation.
  6. Places a single GTT OCO order covering both exits.

Usage:
  python trade.py --symbol INFY --qty 10 --target-pct 5 --sl-pct 2
  python trade.py --symbol INFY --qty 10 --target-pct 5 --sl-pct 2 --order-type LIMIT --price 1500

Setup (one time):
  pip install kiteconnect python-dotenv
  Create a .env file next to this script:
      KITE_API_KEY=your_api_key
      KITE_API_SECRET=your_api_secret
"""

import argparse
import math
import os
import sys
import time
from typing import Optional
from urllib.parse import urlparse, parse_qs

from dotenv import load_dotenv
from kiteconnect import KiteConnect

load_dotenv()

API_KEY = os.getenv("KITE_API_KEY")
API_SECRET = os.getenv("KITE_API_SECRET")

TOKEN_FILE = ".access_token"   # cached for the day, next to this script
TICK_SIZE = 0.05               # standard NSE equity tick size
EXCHANGE = "NSE"
PRODUCT = "CNC"                # per your setup: delivery/equity trades


def die(msg):
    print(f"\n[ERROR] {msg}")
    sys.exit(1)


def round_to_tick(price: float) -> float:
    """Round a price to the nearest valid tick size."""
    return round(round(price / TICK_SIZE) * TICK_SIZE, 2)


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

def get_kite_session() -> KiteConnect:
    if not API_KEY or not API_SECRET:
        die("KITE_API_KEY / KITE_API_SECRET not found. Create a .env file (see script header).")

    kite = KiteConnect(api_key=API_KEY)

    # Reuse a cached token if we already logged in today
    if os.path.exists(TOKEN_FILE):
        with open(TOKEN_FILE) as f:
            saved = f.read().strip()
        if saved:
            kite.set_access_token(saved)
            try:
                kite.profile()  # cheap call to check the token is still valid
                print("[OK] Reusing cached access token.")
                return kite
            except Exception:
                print("[INFO] Cached token expired, need to log in again.")

    # Fresh login flow
    login_url = kite.login_url()
    print("\n1. Open this URL in your browser and log in:\n")
    print(f"   {login_url}\n")
    print("2. After login, your browser will redirect to a URL starting with")
    print("   http://127.0.0.1/?request_token=...  (the page itself will fail to load — that's fine).")
    print("3. Copy that FULL URL from your browser's address bar and paste it below.\n")

    pasted = input("Paste the redirect URL here: ").strip()

    # Accept either the full URL or just the raw request_token
    if pasted.startswith("http"):
        query = parse_qs(urlparse(pasted).query)
        request_token = query.get("request_token", [None])[0]
    else:
        request_token = pasted

    if not request_token:
        die("Could not find request_token in what you pasted.")

    try:
        session = kite.generate_session(request_token, api_secret=API_SECRET)
    except Exception as e:
        die(f"Login failed: {e}")

    access_token = session["access_token"]
    kite.set_access_token(access_token)

    with open(TOKEN_FILE, "w") as f:
        f.write(access_token)

    print("[OK] Logged in, access token cached for today.\n")
    return kite


# ---------------------------------------------------------------------------
# Trading logic
# ---------------------------------------------------------------------------

def place_buy_order(kite: KiteConnect, symbol: str, qty: int, order_type: str, price: Optional[float]) -> str:
    params = dict(
        variety=kite.VARIETY_REGULAR,
        exchange=EXCHANGE,
        tradingsymbol=symbol,
        transaction_type=kite.TRANSACTION_TYPE_BUY,
        quantity=qty,
        product=PRODUCT,
        order_type=order_type,
    )
    if order_type == kite.ORDER_TYPE_LIMIT:
        if price is None:
            die("--price is required when --order-type LIMIT is used.")
        params["price"] = price

    print(f"\nPlacing BUY: {qty} x {symbol} ({order_type})...")
    order_id = kite.place_order(**params)
    print(f"[OK] Order placed. order_id = {order_id}")
    return order_id


def wait_for_fill(kite: KiteConnect, order_id: str, timeout_sec: int = 120, poll_every: float = 2.0) -> float:
    """Poll order status until COMPLETE. Returns the average fill price."""
    print("Waiting for the order to fill", end="", flush=True)
    waited = 0.0

    while waited < timeout_sec:
        history = kite.order_history(order_id)
        last = history[-1]
        status = last["status"]

        if status == "COMPLETE":
            avg_price = float(last["average_price"])
            print(f"\n[OK] Filled at average price: {avg_price}")
            return avg_price

        if status in ("REJECTED", "CANCELLED"):
            die(f"Order was {status}. Reason: {last.get('status_message')}")

        print(".", end="", flush=True)
        time.sleep(poll_every)
        waited += poll_every

    die(f"Order not filled within {timeout_sec}s. Check the order book manually (order_id={order_id}).")


def place_gtt_oco(kite: KiteConnect, symbol: str, qty: int, entry_price: float,
                   target_pct: float, sl_pct: float) -> None:
    target_price = round_to_tick(entry_price * (1 + target_pct / 100))
    sl_price = round_to_tick(entry_price * (1 - sl_pct / 100))

    quote = kite.quote(f"{EXCHANGE}:{symbol}")
    ltp = quote[f"{EXCHANGE}:{symbol}"]["last_price"]

    print("\n--- GTT OCO Preview ---")
    print(f"Symbol:        {symbol}")
    print(f"Quantity:      {qty}")
    print(f"Entry price:   {entry_price}")
    print(f"LTP now:       {ltp}")
    print(f"Target price:  {target_price}  (+{target_pct}%)")
    print(f"Stoploss price:{sl_price}  (-{sl_pct}%)")
    print("-----------------------")

    confirm = input("Place this GTT OCO order? [y/N]: ").strip().lower()
    if confirm != "y":
        print("Cancelled. No GTT was placed. You still hold the shares from the buy order.")
        return

    orders = [
        {
            "transaction_type": kite.TRANSACTION_TYPE_SELL,
            "quantity": qty,
            "order_type": kite.ORDER_TYPE_LIMIT,
            "product": PRODUCT,
            "price": sl_price,
        },
        {
            "transaction_type": kite.TRANSACTION_TYPE_SELL,
            "quantity": qty,
            "order_type": kite.ORDER_TYPE_LIMIT,
            "product": PRODUCT,
            "price": target_price,
        },
    ]

    trigger_id = kite.place_gtt(
        trigger_type=kite.GTT_TYPE_OCO,
        tradingsymbol=symbol,
        exchange=EXCHANGE,
        trigger_values=[sl_price, target_price],
        last_price=ltp,
        orders=orders,
    )
    print(f"[OK] GTT OCO placed. trigger_id = {trigger_id}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Buy + auto GTT OCO (target/stoploss) on Kite.")
    parser.add_argument("--symbol", required=True, help="Trading symbol, e.g. INFY")
    parser.add_argument("--qty", required=True, type=int, help="Quantity to buy")
    parser.add_argument("--target-pct", required=True, type=float, help="Target %% above entry, e.g. 5")
    parser.add_argument("--sl-pct", required=True, type=float, help="Stoploss %% below entry, e.g. 2")
    parser.add_argument("--order-type", default="MARKET", choices=["MARKET", "LIMIT"],
                         help="Buy order type (default: MARKET)")
    parser.add_argument("--price", type=float, default=None, help="Limit price (required if --order-type LIMIT)")
    args = parser.parse_args()

    kite = get_kite_session()

    order_type = kite.ORDER_TYPE_MARKET if args.order_type == "MARKET" else kite.ORDER_TYPE_LIMIT
    order_id = place_buy_order(kite, args.symbol, args.qty, order_type, args.price)
    entry_price = wait_for_fill(kite, order_id)
    place_gtt_oco(kite, args.symbol, args.qty, entry_price, args.target_pct, args.sl_pct)


if __name__ == "__main__":
    main()