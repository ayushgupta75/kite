package com.ayush.kite.orders;

public record BuyOrderRequest(String symbol, int qty, OrderType orderType, Double price) {
}
