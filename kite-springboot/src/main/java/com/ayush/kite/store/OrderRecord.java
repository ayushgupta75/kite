package com.ayush.kite.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderRecord {

    @Id
    private String orderId;

    // Nullable: a postback for an order_id we never seeded (shouldn't normally
    // happen, but updateFromPostback defensively creates a record anyway) has
    // no way to know which app user it belongs to.
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private String status;

    @Column(name = "average_price")
    private Double averagePrice;

    @Column(name = "status_message")
    private String statusMessage;

    @Column(name = "target_pct")
    private Double targetPct;

    @Column(name = "sl_pct")
    private Double slPct;

    protected OrderRecord() {
    }

    OrderRecord(String orderId, String userId, String symbol, int qty) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.qty = qty;
        this.status = "PENDING";
    }

    void update(String status, Double averagePrice, String statusMessage) {
        this.status = status;
        this.averagePrice = averagePrice;
        this.statusMessage = statusMessage;
    }

    void setGttPercentages(double targetPct, double slPct) {
        this.targetPct = targetPct;
        this.slPct = slPct;
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

    public int getQty() {
        return qty;
    }

    public String getStatus() {
        return status;
    }

    public Double getAveragePrice() {
        return averagePrice;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Double getTargetPct() {
        return targetPct;
    }

    public Double getSlPct() {
        return slPct;
    }
}
