package com.ayush.kite.orders;

public record GttPreviewResponse(String orderId, double entryPrice, double ltp, double targetPct, double slPct,
                                  double targetPrice, double slPrice) {
}
