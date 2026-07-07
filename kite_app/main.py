"""
kite_app — FastAPI Kite Connect trading service.

Flow:
  1. GET /login -> redirects to Kite's login page.
  2. Kite redirects back to GET /callback, which exchanges the request_token
     for an access token and caches it.
  3. POST /orders/buy places a CNC buy order and returns immediately.
  4. Kite calls POST /postback (registered in the Kite Connect app console)
     as the order's status changes; GET /orders/{order_id} reflects that.
  5. Once an order is COMPLETE, GET /orders/{order_id}/gtt-preview shows the
     computed target/stoploss prices, and POST /orders/{order_id}/gtt places
     the GTT OCO order covering both exits.

Run with: uvicorn kite_app.main:app --reload
"""

from fastapi import FastAPI

from kite_app import auth, orders

app = FastAPI(title="Kite Trading API", swagger_ui_parameters={"tryItOutEnabled": True})
app.include_router(auth.router)
app.include_router(orders.router)
