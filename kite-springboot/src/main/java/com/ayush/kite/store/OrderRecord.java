package com.ayush.kite.store;

public record OrderRecord(String orderId, String symbol, int qty, String status, Double averagePrice, String statusMessage) {
}
