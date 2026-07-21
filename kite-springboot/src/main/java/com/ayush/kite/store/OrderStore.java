package com.ayush.kite.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderStore {

    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();

    public void seed(String orderId, String symbol, int qty) {
        orders.put(orderId, new OrderRecord(orderId, symbol, qty, "PENDING", null, null));
    }

    public void updateFromPostback(Map<String, Object> payload) {
        String orderId = (String) payload.get("order_id");
        OrderRecord existing = orders.get(orderId);

        String symbol = existing != null ? existing.symbol() : (String) payload.get("tradingsymbol");
        int qty = existing != null ? existing.qty() : toInt(payload.get("quantity"));
        Double averagePrice = existing != null ? existing.averagePrice() : null;

        Object averagePriceRaw = payload.get("average_price");
        if (averagePriceRaw != null && !"0".equals(String.valueOf(averagePriceRaw)) && !"0.0".equals(String.valueOf(averagePriceRaw))) {
            averagePrice = Double.parseDouble(String.valueOf(averagePriceRaw));
        }

        String status = (String) payload.get("status");
        String statusMessage = (String) payload.get("status_message");

        orders.put(orderId, new OrderRecord(orderId, symbol, qty, status, averagePrice, statusMessage));
    }

    public Optional<OrderRecord> get(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    private static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
