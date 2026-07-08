from typing import Literal, Optional

from pydantic import BaseModel


class BuyOrderRequest(BaseModel):
    symbol: str
    qty: int
    order_type: Literal["MARKET", "LIMIT"] = "MARKET"
    price: Optional[float] = None
    # Required by Kite for MARKET orders. -1 = system-default automatic
    # protection; 0-100 = explicit percentage. Ignored for LIMIT orders.
    # market_protection: Optional[float] = None


class BuyOrderResponse(BaseModel):
    order_id: str


class OrderStatusResponse(BaseModel):
    order_id: str
    symbol: str
    qty: int
    status: str
    average_price: Optional[float] = None
    status_message: Optional[str] = None


class GttPreviewResponse(BaseModel):
    order_id: str
    entry_price: float
    ltp: float
    target_pct: float
    sl_pct: float
    target_price: float
    sl_price: float


class PriceGttPreviewResponse(BaseModel):
    price: float
    target_pct: float
    sl_pct: float
    target_price: float
    sl_price: float


class GttRequest(BaseModel):
    target_pct: float
    sl_pct: float


class GttResponse(BaseModel):
    trigger_id: str
    target_price: float
    sl_price: float
