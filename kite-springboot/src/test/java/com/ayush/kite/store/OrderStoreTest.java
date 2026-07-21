package com.ayush.kite.store;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStoreTest {

    @Test
    void seed_createsPendingRecord() {
        OrderStore store = new OrderStore();
        store.seed("order-1", "INFY", 10);

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.status()).isEqualTo("PENDING");
        assertThat(record.averagePrice()).isNull();
        assertThat(record.symbol()).isEqualTo("INFY");
        assertThat(record.qty()).isEqualTo(10);
    }

    @Test
    void updateFromPostback_updatesStatusAndAveragePrice() {
        OrderStore store = new OrderStore();
        store.seed("order-1", "INFY", 10);

        store.updateFromPostback(Map.of(
                "order_id", "order-1",
                "status", "COMPLETE",
                "average_price", "1450.5"
        ));

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.status()).isEqualTo("COMPLETE");
        assertThat(record.averagePrice()).isEqualTo(1450.5);
        assertThat(record.symbol()).isEqualTo("INFY");
        assertThat(record.qty()).isEqualTo(10);
    }

    @Test
    void updateFromPostback_falsyAveragePrice_doesNotClobberExisting() {
        OrderStore store = new OrderStore();
        store.seed("order-1", "INFY", 10);
        store.updateFromPostback(Map.of("order_id", "order-1", "status", "COMPLETE", "average_price", "1450.5"));

        store.updateFromPostback(Map.of("order_id", "order-1", "status", "COMPLETE", "average_price", "0"));

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.averagePrice()).isEqualTo(1450.5);
    }

    @Test
    void updateFromPostback_unseededOrder_createsEntryFromPayload() {
        OrderStore store = new OrderStore();

        store.updateFromPostback(Map.of(
                "order_id", "order-2",
                "tradingsymbol", "TCS",
                "quantity", 5,
                "status", "OPEN"
        ));

        OrderRecord record = store.get("order-2").orElseThrow();
        assertThat(record.symbol()).isEqualTo("TCS");
        assertThat(record.qty()).isEqualTo(5);
        assertThat(record.status()).isEqualTo("OPEN");
    }

    @Test
    void get_unknownOrder_returnsEmpty() {
        OrderStore store = new OrderStore();
        assertThat(store.get("does-not-exist")).isEmpty();
    }
}
