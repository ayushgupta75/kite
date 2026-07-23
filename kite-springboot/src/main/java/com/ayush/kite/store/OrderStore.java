package com.ayush.kite.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class OrderStore {

    private final OrderRepository orderRepository;

    public OrderStore(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void seed(String orderId, String userId, String symbol, int qty) {
        orderRepository.save(new OrderRecord(orderId, userId, symbol, qty));
    }

    public void updateFromPostback(Map<String, Object> payload) {
        String orderId = (String) payload.get("order_id");
        OrderRecord record = orderRepository.findById(orderId)
                .orElseGet(() -> new OrderRecord(orderId, null, (String) payload.get("tradingsymbol"), toInt(payload.get("quantity"))));

        Double averagePrice = record.getAveragePrice();
        Object averagePriceRaw = payload.get("average_price");
        if (averagePriceRaw != null && !"0".equals(String.valueOf(averagePriceRaw)) && !"0.0".equals(String.valueOf(averagePriceRaw))) {
            averagePrice = Double.parseDouble(String.valueOf(averagePriceRaw));
        }

        record.update((String) payload.get("status"), averagePrice, (String) payload.get("status_message"));
        orderRepository.save(record);
    }

    public Optional<OrderRecord> get(String orderId) {
        return orderRepository.findById(orderId);
    }

    public void setGttPercentages(String orderId, double targetPct, double slPct) {
        OrderRecord record = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order_id=" + orderId));
        record.setGttPercentages(targetPct, slPct);
        orderRepository.save(record);
    }

    private static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
