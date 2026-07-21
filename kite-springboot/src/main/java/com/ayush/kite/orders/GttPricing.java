package com.ayush.kite.orders;

public final class GttPricing {

    private static final double TICK_SIZE = 0.05;

    private GttPricing() {
    }

    public static double roundToTick(double price) {
        return Math.round(Math.round(price / TICK_SIZE) * TICK_SIZE * 100.0) / 100.0;
    }

    public static double targetPrice(double entryPrice, double targetPct) {
        return roundToTick(entryPrice * (1 + targetPct / 100));
    }

    public static double slPrice(double entryPrice, double slPct) {
        return roundToTick(entryPrice * (1 - slPct / 100));
    }
}
