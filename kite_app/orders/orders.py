import hashlib

from fastapi import APIRouter, HTTPException, Request

from kite_app.config import API_SECRET, EXCHANGE_NSE, PRODUCT, round_to_tick
from kite_app.kite_client import get_kite
from kite_app.schemas import (
    BuyOrderRequest,
    BuyOrderResponse,
    GttPreviewResponse,
    GttRequest,
    GttResponse,
    OrderStatusResponse,
    PriceGttPreviewResponse,
)
from kite_app.store import get_order, seed_order, update_from_postback

router = APIRouter()


@router.post("/orders/buy", response_model=BuyOrderResponse)
def buy(request: BuyOrderRequest) -> BuyOrderResponse:
    #request logic
    #tbd


    # business logic
    if request.order_type == "LIMIT" and request.price is None:
        raise HTTPException(status_code=400, detail="price is required when order_type is LIMIT")

    kite = get_kite()
    params = dict(
        variety=kite.VARIETY_REGULAR,
        exchange=EXCHANGE_NSE,
        tradingsymbol=request.symbol,
        transaction_type=kite.TRANSACTION_TYPE_BUY,
        quantity=request.qty,
        product=PRODUCT,
        order_type=request.order_type,
    )
    if request.order_type == "LIMIT":
        params["price"] = request.price
    else:
        params["market_protection"] = (
            request.market_protection if request.market_protection is not None else -1
        )

    #client
    order_id = kite.place_order(**params)

    #db
    seed_order(order_id, request.symbol, request.qty)

    return BuyOrderResponse(order_id=order_id)


@router.post("/postback")
async def postback(request: Request) -> dict:
    payload = await request.json()
    order_id = payload.get("order_id")
    order_timestamp = payload.get("order_timestamp")
    checksum = payload.get("checksum")
    if not order_id or not order_timestamp or not checksum:
        raise HTTPException(status_code=400, detail="Malformed postback payload.")


    expected = hashlib.sha256(
        (order_id + order_timestamp + API_SECRET).encode("utf-8")
    ).hexdigest()
    if checksum != expected:
        raise HTTPException(status_code=401, detail="Checksum mismatch.")
    
    #db
    update_from_postback(payload)

    return {"status": "ok"}


@router.get("/orders/{order_id}", response_model=OrderStatusResponse)
def order_status(order_id: str) -> OrderStatusResponse:

    #business and db
    order = get_order(order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="Unknown order_id.")
    
    return OrderStatusResponse(**order)


@router.get("/gtt-preview", response_model=PriceGttPreviewResponse)
def gtt_preview_from_price(price: float, target_pct: float, sl_pct: float) -> PriceGttPreviewResponse:
    target_price, sl_price = _compute_prices(price, target_pct, sl_pct)
    return PriceGttPreviewResponse(
        price=price,
        target_pct=target_pct,
        sl_pct=sl_pct,
        target_price=target_price,
        sl_price=sl_price,
    )


@router.get("/orders/{order_id}/gtt-preview", response_model=GttPreviewResponse)
def gtt_preview(order_id: str, target_pct: float, sl_pct: float) -> GttPreviewResponse:
    order = _require_filled_order(order_id)
    entry_price = order["average_price"]
    target_price, sl_price = _compute_prices(entry_price, target_pct, sl_pct)

    kite = get_kite()
    quote = kite.quote(f"{EXCHANGE_NSE}:{order['symbol']}")
    ltp = quote[f"{EXCHANGE_NSE}:{order['symbol']}"]["last_price"]

    return GttPreviewResponse(
        order_id=order_id,
        entry_price=entry_price,
        ltp=ltp,
        target_pct=target_pct,
        sl_pct=sl_pct,
        target_price=target_price,
        sl_price=sl_price,
    )


@router.post("/orders/{order_id}/gtt", response_model=GttResponse)
def place_gtt(order_id: str, req: GttRequest) -> GttResponse:
    order = _require_filled_order(order_id)
    entry_price = order["average_price"]
    symbol = order["symbol"]
    qty = order["qty"]
    target_price, sl_price = _compute_prices(entry_price, req.target_pct, req.sl_pct)

    kite = get_kite()
    quote = kite.quote(f"{EXCHANGE_NSE}:{symbol}")
    ltp = quote[f"{EXCHANGE_NSE}:{symbol}"]["last_price"]

    gtt_orders = [
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

    result = kite.place_gtt(
        trigger_type=kite.GTT_TYPE_OCO,
        tradingsymbol=symbol,
        exchange=EXCHANGE_NSE,
        trigger_values=[sl_price, target_price],
        last_price=ltp,
        orders=gtt_orders,
    )
    return GttResponse(trigger_id=str(result["trigger_id"]), target_price=target_price, sl_price=sl_price)





# private functions

def _compute_prices(entry_price: float, target_pct: float, sl_pct: float) -> tuple[float, float]:
    target_price = round_to_tick(entry_price * (1 + target_pct / 100))
    sl_price = round_to_tick(entry_price * (1 - sl_pct / 100))
    return target_price, sl_price



def _require_filled_order(order_id: str) -> dict:
    order = get_order(order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="Unknown order_id.")
    if order["status"] != "COMPLETE" or order["average_price"] is None:
        raise HTTPException(status_code=409, detail=f"Order is not filled yet (status={order['status']}).")
    return order
