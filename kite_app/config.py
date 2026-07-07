import os

from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("KITE_API_KEY")
API_SECRET = os.getenv("KITE_API_SECRET")

TOKEN_FILE = ".access_token"   # cached for the day, next to this script
TICK_SIZE = 0.05               # standard NSE equity tick size
EXCHANGE = "NSE"
PRODUCT = "CNC"                # per your setup: delivery/equity trades


def round_to_tick(price: float) -> float:
    """Round a price to the nearest valid tick size."""
    return round(round(price / TICK_SIZE) * TICK_SIZE, 2)
