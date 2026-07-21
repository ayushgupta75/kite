package com.ayush.kite.orders;

public record OrderStatusResponse(String orderId, String symbol, int qty, String status, Double averagePrice, String statusMessage) {
}
