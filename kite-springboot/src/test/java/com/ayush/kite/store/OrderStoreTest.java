package com.ayush.kite.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderStoreTest {

    @Autowired
    private OrderRepository orderRepository;

    private OrderStore store;

    @BeforeEach
    void setUp() {
        store = new OrderStore(orderRepository);
    }

    @Test
    void seed_createsPendingRecord() {
        store.seed("order-1", "ayush", "INFY", 10);

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.getStatus()).isEqualTo("PENDING");
        assertThat(record.getAveragePrice()).isNull();
        assertThat(record.getUserId()).isEqualTo("ayush");
        assertThat(record.getSymbol()).isEqualTo("INFY");
        assertThat(record.getQty()).isEqualTo(10);
    }

    @Test
    void updateFromPostback_updatesStatusAndAveragePrice() {
        store.seed("order-1", "ayush", "INFY", 10);

        store.updateFromPostback(Map.of(
                "order_id", "order-1",
                "status", "COMPLETE",
                "average_price", "1450.5"
        ));

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.getStatus()).isEqualTo("COMPLETE");
        assertThat(record.getAveragePrice()).isEqualTo(1450.5);
        assertThat(record.getSymbol()).isEqualTo("INFY");
        assertThat(record.getQty()).isEqualTo(10);
    }

    @Test
    void updateFromPostback_falsyAveragePrice_doesNotClobberExisting() {
        store.seed("order-1", "ayush", "INFY", 10);
        store.updateFromPostback(Map.of("order_id", "order-1", "status", "COMPLETE", "average_price", "1450.5"));

        store.updateFromPostback(Map.of("order_id", "order-1", "status", "COMPLETE", "average_price", "0"));

        OrderRecord record = store.get("order-1").orElseThrow();
        assertThat(record.getAveragePrice()).isEqualTo(1450.5);
    }

    @Test
    void updateFromPostback_unseededOrder_createsEntryFromPayload() {
        store.updateFromPostback(Map.of(
                "order_id", "order-2",
                "tradingsymbol", "TCS",
                "quantity", 5,
                "status", "OPEN"
        ));

        OrderRecord record = store.get("order-2").orElseThrow();
        assertThat(record.getUserId()).isNull();
        assertThat(record.getSymbol()).isEqualTo("TCS");
        assertThat(record.getQty()).isEqualTo(5);
        assertThat(record.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void get_unknownOrder_returnsEmpty() {
        assertThat(store.get("does-not-exist")).isEmpty();
    }
}
