from fastapi import HTTPException

from kite_app.config import EXCHANGE_NSE, PRODUCT
from kite_app.schemas import BuyOrderResponse, OrderType
from kite_app.store import seed_order
from kite_client import get_kite

def buyStock(request):

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

    if request.order_type == OrderType.LIMIT:
        params["price"] = request.price
    else:
        params["market_protection"] = -1

    #client
    order_id = kite.place_order(**params)

    #db
    seed_order(order_id, request.symbol, request.qty)

    return BuyOrderResponse(order_id=order_id)