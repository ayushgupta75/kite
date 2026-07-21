package com.ayush.kite.orders;

import com.ayush.kite.client.KiteClientFactory;
import com.ayush.kite.store.OrderStore;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private static final String API_SECRET = "test-secret";

    private KiteClientFactory kiteClientFactory;
    private OrderStore orderStore;
    private OrderController controller;
    private KiteConnect kite;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        kiteClientFactory = mock(KiteClientFactory.class);
        orderStore = new OrderStore();
        kite = mock(KiteConnect.class);
        session = mock(HttpSession.class);

        controller = new OrderController(kiteClientFactory, orderStore);
        ReflectionTestUtils.setField(controller, "apiSecret", API_SECRET);
    }

    @Test
    void buy_withoutLogin_throwsUnauthorized() {
        when(session.getAttribute("userId")).thenReturn(null);

        assertThatThrownBy(() -> controller.buy(new BuyOrderRequest("INFY", 1, OrderType.MARKET, null), session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void buy_limitWithoutPrice_throwsBadRequest() {
        when(session.getAttribute("userId")).thenReturn("ayush");

        assertThatThrownBy(() -> controller.buy(new BuyOrderRequest("INFY", 1, OrderType.LIMIT, null), session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void buy_marketOrder_placesOrderWithMarketProtection() throws Throwable {
        when(session.getAttribute("userId")).thenReturn("ayush");
        when(kiteClientFactory.forUser("ayush")).thenReturn(kite);

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.orderId = "order-123";
        when(kite.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR))).thenReturn(orderResponse);

        BuyOrderResponse response = controller.buy(new BuyOrderRequest("INFY", 10, OrderType.MARKET, null), session);

        assertThat(response.orderId()).isEqualTo("order-123");

        ArgumentCaptor<OrderParams> captor = ArgumentCaptor.forClass(OrderParams.class);
        org.mockito.Mockito.verify(kite).placeOrder(captor.capture(), eq(Constants.VARIETY_REGULAR));
        OrderParams params = captor.getValue();
        assertThat(params.exchange).isEqualTo(Constants.EXCHANGE_NSE);
        assertThat(params.tradingsymbol).isEqualTo("INFY");
        assertThat(params.transactionType).isEqualTo(Constants.TRANSACTION_TYPE_BUY);
        assertThat(params.quantity).isEqualTo(10);
        assertThat(params.product).isEqualTo(Constants.PRODUCT_CNC);
        assertThat(params.orderType).isEqualTo(Constants.ORDER_TYPE_MARKET);
        assertThat(params.marketProtection).isEqualTo(-1);
        assertThat(params.price).isNull();

        assertThat(orderStore.get("order-123")).isPresent();
        assertThat(orderStore.get("order-123").get().status()).isEqualTo("PENDING");
    }

    @Test
    void buy_limitOrder_placesOrderWithPrice() throws Throwable {
        when(session.getAttribute("userId")).thenReturn("ayush");
        when(kiteClientFactory.forUser("ayush")).thenReturn(kite);

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.orderId = "order-456";
        when(kite.placeOrder(any(OrderParams.class), eq(Constants.VARIETY_REGULAR))).thenReturn(orderResponse);

        controller.buy(new BuyOrderRequest("TCS", 5, OrderType.LIMIT, 3500.0), session);

        ArgumentCaptor<OrderParams> captor = ArgumentCaptor.forClass(OrderParams.class);
        org.mockito.Mockito.verify(kite).placeOrder(captor.capture(), eq(Constants.VARIETY_REGULAR));
        OrderParams params = captor.getValue();
        assertThat(params.orderType).isEqualTo(Constants.ORDER_TYPE_LIMIT);
        assertThat(params.price).isEqualTo(3500.0);
    }

    @Test
    void postback_missingFields_throwsBadRequest() {
        assertThatThrownBy(() -> controller.postback(Map.of("order_id", "order-123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void postback_badChecksum_throwsUnauthorized() {
        Map<String, Object> payload = Map.of(
                "order_id", "order-123",
                "order_timestamp", "2026-07-21 10:00:00",
                "checksum", "wrong-checksum"
        );

        assertThatThrownBy(() -> controller.postback(payload))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void postback_validChecksum_updatesStore() {
        orderStore.seed("order-123", "INFY", 10);

        String orderId = "order-123";
        String orderTimestamp = "2026-07-21 10:00:00";
        String checksum = DigestUtils.sha256Hex(orderId + orderTimestamp + API_SECRET);

        controller.postback(Map.of(
                "order_id", orderId,
                "order_timestamp", orderTimestamp,
                "checksum", checksum,
                "status", "COMPLETE",
                "average_price", "1450.5"
        ));

        assertThat(orderStore.get("order-123").get().status()).isEqualTo("COMPLETE");
        assertThat(orderStore.get("order-123").get().averagePrice()).isEqualTo(1450.5);
    }

    @Test
    void getOrder_unknown_throwsNotFound() {
        assertThatThrownBy(() -> controller.getOrder("does-not-exist"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getOrder_known_returnsStatus() {
        orderStore.seed("order-123", "INFY", 10);

        OrderStatusResponse response = controller.getOrder("order-123");

        assertThat(response.orderId()).isEqualTo("order-123");
        assertThat(response.symbol()).isEqualTo("INFY");
        assertThat(response.qty()).isEqualTo(10);
        assertThat(response.status()).isEqualTo("PENDING");
    }
}
