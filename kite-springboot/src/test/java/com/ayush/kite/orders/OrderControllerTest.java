package com.ayush.kite.orders;

import com.ayush.kite.client.KiteClientFactory;
import com.ayush.kite.store.GttRecord;
import com.ayush.kite.store.GttRepository;
import com.ayush.kite.store.OrderRepository;
import com.ayush.kite.store.OrderStore;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.GTT;
import com.zerodhatech.models.GTTParams;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Quote;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
class OrderControllerTest {

    private static final String API_SECRET = "test-secret";

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private GttRepository gttRepository;

    private KiteClientFactory kiteClientFactory;
    private OrderStore orderStore;
    private OrderController controller;
    private KiteConnect kite;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        kiteClientFactory = mock(KiteClientFactory.class);
        orderStore = new OrderStore(orderRepository);
        kite = mock(KiteConnect.class);
        session = mock(HttpSession.class);

        controller = new OrderController(kiteClientFactory, orderStore, gttRepository);
        ReflectionTestUtils.setField(controller, "apiSecret", API_SECRET);
    }

    private static Quote quote(long instrumentToken, double lastPrice) {
        Quote quote = new Quote();
        quote.instrumentToken = instrumentToken;
        quote.lastPrice = lastPrice;
        return quote;
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
        assertThat(orderStore.get("order-123").get().getUserId()).isEqualTo("ayush");
        assertThat(orderStore.get("order-123").get().getStatus()).isEqualTo("PENDING");
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
        orderStore.seed("order-123", "ayush", "INFY", 10);

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

        assertThat(orderStore.get("order-123").get().getStatus()).isEqualTo("COMPLETE");
        assertThat(orderStore.get("order-123").get().getAveragePrice()).isEqualTo(1450.5);
    }

    @Test
    void getOrder_withoutLogin_throwsUnauthorized() {
        when(session.getAttribute("userId")).thenReturn(null);

        assertThatThrownBy(() -> controller.getOrder("order-123", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void getOrder_unknown_throwsNotFound() {
        when(session.getAttribute("userId")).thenReturn("ayush");

        assertThatThrownBy(() -> controller.getOrder("does-not-exist", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getOrder_ownedByDifferentUser_throwsNotFound() {
        orderStore.seed("order-123", "someone-else", "INFY", 10);
        when(session.getAttribute("userId")).thenReturn("ayush");

        assertThatThrownBy(() -> controller.getOrder("order-123", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getOrder_known_returnsStatus() {
        orderStore.seed("order-123", "ayush", "INFY", 10);
        when(session.getAttribute("userId")).thenReturn("ayush");

        OrderStatusResponse response = controller.getOrder("order-123", session);

        assertThat(response.orderId()).isEqualTo("order-123");
        assertThat(response.symbol()).isEqualTo("INFY");
        assertThat(response.qty()).isEqualTo(10);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void gttPreview_orderNotFilled_throwsConflict() {
        orderStore.seed("order-123", "ayush", "INFY", 10);
        when(session.getAttribute("userId")).thenReturn("ayush");

        assertThatThrownBy(() -> controller.gttPreview("order-123", 3.0, 1.5, session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void gttPreview_unknownOrder_throwsNotFound() {
        when(session.getAttribute("userId")).thenReturn("ayush");

        assertThatThrownBy(() -> controller.gttPreview("does-not-exist", 3.0, 1.5, session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void gttPreview_filledOrder_computesPrices() throws Throwable {
        orderStore.seed("order-123", "ayush", "INFY", 10);
        orderStore.updateFromPostback(Map.of("order_id", "order-123", "status", "COMPLETE", "average_price", "1000"));

        when(session.getAttribute("userId")).thenReturn("ayush");
        when(kiteClientFactory.forUser("ayush")).thenReturn(kite);
        when(kite.getQuote(new String[]{"NSE:INFY"})).thenReturn(Map.of("NSE:INFY", quote(408065, 1010.0)));

        GttPreviewResponse response = controller.gttPreview("order-123", 3.0, 1.5, session);

        assertThat(response.entryPrice()).isEqualTo(1000.0);
        assertThat(response.ltp()).isEqualTo(1010.0);
        assertThat(response.targetPrice()).isEqualTo(1030.0);
        assertThat(response.slPrice()).isEqualTo(985.0);
    }

    @Test
    void placeGtt_filledOrder_placesOcoGttAndPersistsRecord() throws Throwable {
        orderStore.seed("order-123", "ayush", "INFY", 10);
        orderStore.updateFromPostback(Map.of("order_id", "order-123", "status", "COMPLETE", "average_price", "1000"));

        when(session.getAttribute("userId")).thenReturn("ayush");
        when(kiteClientFactory.forUser("ayush")).thenReturn(kite);
        when(kite.getQuote(new String[]{"NSE:INFY"})).thenReturn(Map.of("NSE:INFY", quote(408065, 1010.0)));

        GTT gttResult = new GTT();
        gttResult.id = 555;
        when(kite.placeGTT(any(GTTParams.class))).thenReturn(gttResult);

        GttResponse response = controller.placeGtt("order-123", new GttRequest(3.0, 1.5), session);

        assertThat(response.triggerId()).isEqualTo(555);
        assertThat(response.targetPrice()).isEqualTo(1030.0);
        assertThat(response.slPrice()).isEqualTo(985.0);

        ArgumentCaptor<GTTParams> captor = ArgumentCaptor.forClass(GTTParams.class);
        org.mockito.Mockito.verify(kite).placeGTT(captor.capture());
        GTTParams params = captor.getValue();
        assertThat(params.tradingsymbol).isEqualTo("INFY");
        assertThat(params.exchange).isEqualTo(Constants.EXCHANGE_NSE);
        assertThat(params.instrumentToken).isEqualTo(408065);
        assertThat(params.triggerType).isEqualTo(Constants.OCO);
        assertThat(params.triggerPrices).containsExactly(1030.0, 985.0);
        assertThat(params.orders).hasSize(2);
        assertThat(params.orders.get(0).price).isEqualTo(1030.0);
        assertThat(params.orders.get(0).transactionType).isEqualTo(Constants.TRANSACTION_TYPE_SELL);
        assertThat(params.orders.get(0).quantity).isEqualTo(10);
        assertThat(params.orders.get(1).price).isEqualTo(985.0);

        GttRecord saved = gttRepository.findById(555).orElseThrow();
        assertThat(saved.getOrderId()).isEqualTo("order-123");
        assertThat(saved.getUserId()).isEqualTo("ayush");
        assertThat(saved.getSymbol()).isEqualTo("INFY");
        assertThat(saved.getTargetPrice()).isEqualTo(1030.0);
        assertThat(saved.getSlPrice()).isEqualTo(985.0);
    }
}
