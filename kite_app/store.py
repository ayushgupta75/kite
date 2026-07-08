from typing import Optional

# In-memory, single-process order tracking. Lost on restart — acceptable for
# a single-user tool; the cached access token (kite_client.TOKEN_FILE) is the
# only thing that needs to survive restarts.
ORDER_STORE: dict[str, dict] = {}


def seed_order(order_id: str, symbol: str, qty: int) -> None:
    ORDER_STORE[order_id] = {
        "order_id": order_id,
        "symbol": symbol,
        "qty": qty,
        "status": "PENDING",
        "average_price": None,
        "status_message": None,
    }


def update_from_postback(payload: dict) -> None:
    order_id = payload["order_id"]
    
    entry = ORDER_STORE.setdefault(order_id, {
        "order_id": order_id,
        "symbol": payload.get("tradingsymbol"),
        "qty": payload.get("quantity"),
        "average_price": None,
        "status_message": None,
    })

    entry["status"] = payload.get("status")
    if payload.get("average_price"):
        entry["average_price"] = float(payload["average_price"])
    entry["status_message"] = payload.get("status_message")


def get_order(order_id: str) -> Optional[dict]:
    return ORDER_STORE.get(order_id)
