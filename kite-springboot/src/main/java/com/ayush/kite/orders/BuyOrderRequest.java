package com.ayush.kite.orders;

public record BuyOrderRequest(
        String symbol,
        int qty,
        OrderType orderType,
        double price,
        double targetPct,
        double slPct
) {}
