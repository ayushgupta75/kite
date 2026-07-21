package com.ayush.kite.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "gtts")
public class GttRecord {

    @Id
    private Integer triggerId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "target_price", nullable = false)
    private double targetPrice;

    @Column(name = "sl_price", nullable = false)
    private double slPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GttRecord() {
    }

    public GttRecord(Integer triggerId, String orderId, String userId, String symbol, double targetPrice, double slPrice) {
        this.triggerId = triggerId;
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.targetPrice = targetPrice;
        this.slPrice = slPrice;
    }

    public Integer getTriggerId() {
        return triggerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    public double getSlPrice() {
        return slPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
