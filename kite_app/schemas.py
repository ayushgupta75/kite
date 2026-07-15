from typing import Literal, Optional

from pydantic import BaseModel, field_validator

from enum import Enum

# Simple Enum
class OrderType(Enum):
    MARKET = "MARKET"
    LIMIT = "LIMIT"
    AMO = "AMO"
    DELIVERED = "DELIVERED"
    CANCELLED = "CANCELLED"

class BuyOrderRequest(BaseModel):
    symbol: str
    qty: int
    order_type: OrderType = OrderType.MARKET
    price: Optional[float] = None
    # Accepted but currently unused — market_protection is hardcoded to -1
    # in orders.py regardless of what's passed here.
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
