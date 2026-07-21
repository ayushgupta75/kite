package com.ayush.kite.orders;

import com.ayush.kite.client.KiteClientFactory;
import com.ayush.kite.store.OrderRecord;
import com.ayush.kite.store.OrderStore;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
public class OrderController {

    @Value("${kite.api-secret}")
    private String apiSecret;

    private final KiteClientFactory kiteClientFactory;
    private final OrderStore orderStore;

    public OrderController(KiteClientFactory kiteClientFactory, OrderStore orderStore) {
        this.kiteClientFactory = kiteClientFactory;
        this.orderStore = orderStore;
    }

    @PostMapping("/orders/buy")
    public BuyOrderResponse buy(@RequestBody BuyOrderRequest request, HttpSession session) {
        String userId = requireLoggedIn(session);

        if (request.orderType() == OrderType.LIMIT && request.price() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price is required when orderType is LIMIT");
        }

        KiteConnect kite = kiteClientFactory.forUser(userId);

        OrderParams params = new OrderParams();
        params.exchange = Constants.EXCHANGE_NSE;
        params.tradingsymbol = request.symbol();
        params.transactionType = Constants.TRANSACTION_TYPE_BUY;
        params.quantity = request.qty();
        params.product = Constants.PRODUCT_CNC;
        params.validity = Constants.VALIDITY_DAY;

        if (request.orderType() == OrderType.LIMIT) {
            params.orderType = Constants.ORDER_TYPE_LIMIT;
            params.price = request.price();
        } else {
            params.orderType = Constants.ORDER_TYPE_MARKET;
            params.marketProtection = -1;
        }

        OrderResponse response;
        try {
                response = kite.placeOrder(params, Constants.VARIETY_REGULAR);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order placement failed: " + e.getMessage());
        }

        orderStore.seed(response.orderId, request.symbol(), request.qty());

        return new BuyOrderResponse(response.orderId);
    }

    @PostMapping("/postback")
    public void postback(@RequestBody Map<String, Object> payload) {
        Object orderId = payload.get("order_id");
        Object orderTimestamp = payload.get("order_timestamp");
        Object checksum = payload.get("checksum");

        if (orderId == null || orderTimestamp == null || checksum == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed postback payload.");
        }

        String expected = DigestUtils.sha256Hex(String.valueOf(orderId) + orderTimestamp + apiSecret);
        if (!expected.equals(checksum)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Checksum mismatch.");
        }

        orderStore.updateFromPostback(payload);
    }

    @GetMapping("/orders/{orderId}")
    public OrderStatusResponse getOrder(@PathVariable String orderId) {
        OrderRecord record = orderStore.get(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown order_id."));

        return new OrderStatusResponse(record.orderId(), record.symbol(), record.qty(), record.status(),
                record.averagePrice(), record.statusMessage());
    }

    private String requireLoggedIn(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first via POST /auth/login.");
        }
        return (String) userId;
    }
}
