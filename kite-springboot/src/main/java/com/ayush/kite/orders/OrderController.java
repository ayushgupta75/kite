package com.ayush.kite.orders;

import com.ayush.kite.client.KiteClientFactory;
import com.ayush.kite.store.*;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.GTT;
import com.zerodhatech.models.GTTParams;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Quote;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderRepository orderRepository;
    @Value("${kite.api-secret}")
    private String apiSecret;

    private final KiteClientFactory kiteClientFactory;
    private final OrderStore orderStore;
    private final GttRepository gttRepository;

    public OrderController(KiteClientFactory kiteClientFactory, OrderStore orderStore, GttRepository gttRepository, OrderRepository orderRepository) {
        this.kiteClientFactory = kiteClientFactory;
        this.orderStore = orderStore;
        this.gttRepository = gttRepository;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders/buy")
    public BuyOrderResponse buy(@RequestBody BuyOrderRequest request, HttpSession session) {
        String userId = requireLoggedIn(session);

        KiteConnect kite = kiteClientFactory.forUser(userId);

        OrderParams params = new OrderParams();
        params.exchange = Constants.EXCHANGE_NSE;
        params.tradingsymbol = request.symbol();
        params.transactionType = Constants.TRANSACTION_TYPE_BUY;
        params.quantity = request.qty();
        params.product = Constants.PRODUCT_CNC;
        params.validity = Constants.VALIDITY_DAY;

//        if (request.orderType() == OrderType.LIMIT) {
        params.orderType = Constants.ORDER_TYPE_LIMIT;
        params.price = request.price();
//        } else {
//            params.orderType = Constants.ORDER_TYPE_MARKET;
//            params.marketProtection = -1;
//        }

        OrderResponse response;
        try {
                response = kite.placeOrder(params, Constants.VARIETY_REGULAR);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order placement failed: " + e.getMessage());
        }

        orderStore.seed(response.orderId, userId, request.symbol(), request.qty());
        orderStore.setGttPercentages(response.orderId, request.targetPct(), request.slPct());

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

        orderStore.updateOrderState(payload);

    }

    @GetMapping("/orders/{orderId}")
    public OrderStatusResponse getOrder(@PathVariable String orderId, HttpSession session) {
        String userId = requireLoggedIn(session);

        OrderRecord record = orderStore.get(orderId)
                .filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown order_id."));

        return new OrderStatusResponse(record.getOrderId(), record.getSymbol(), record.getQty(), record.getStatus(),
                record.getAveragePrice(), record.getStatusMessage());
    }

    @GetMapping("/orders/{orderId}/gtt-preview")
    public GttPreviewResponse gttPreview(@PathVariable String orderId, @RequestParam double targetPct,
                                          @RequestParam double slPct, HttpSession session) {
        String userId = requireLoggedIn(session);
        OrderRecord record = requireFilledOwnedOrder(orderId, userId);

        KiteConnect kite = kiteClientFactory.forUser(userId);
        Quote quote = fetchQuote(kite, record.getSymbol());

        double entryPrice = record.getAveragePrice();
        double targetPrice = GttPricing.targetPrice(entryPrice, targetPct);
        double slPrice = GttPricing.slPrice(entryPrice, slPct);

        return new GttPreviewResponse(orderId, entryPrice, quote.lastPrice, targetPct, slPct, targetPrice, slPrice);
    }

    @PostMapping("/orders/{orderId}/gtt")
    public GttResponse placeGtt(@PathVariable String orderId, @RequestBody GttRequest request, HttpSession session) {
        String userId = requireLoggedIn(session);
        OrderRecord record = requireFilledOwnedOrder(orderId, userId);
        return placeGttForOrder(record, request.targetPct(), request.slPct());
    }

    // No HttpSession here on purpose: this is called both from the session-authenticated
    // placeGtt() endpoint above and directly from postback() (a server-to-server webhook from
    // Zerodha with no app session at all) - record.getUserId() is who actually owns the order
    // regardless of who/what triggered this.
    private GttResponse placeGttForOrder(OrderRecord record, double targetPct, double slPct) {
        String userId = record.getUserId();
        KiteConnect kite = kiteClientFactory.forUser(userId);
        Quote quote = fetchQuote(kite, record.getSymbol());

        double entryPrice = record.getAveragePrice();
        double targetPrice = GttPricing.targetPrice(entryPrice, targetPct);
        double slPrice = GttPricing.slPrice(entryPrice, slPct);

        GTTParams params = new GTTParams();
        params.tradingsymbol = record.getSymbol();
        params.exchange = Constants.EXCHANGE_NSE;
        params.instrumentToken = (int) quote.instrumentToken;
        params.triggerType = Constants.OCO;
        params.lastPrice = quote.lastPrice;
        params.triggerPrices = List.of(targetPrice, slPrice);

        GTTParams.GTTOrderParams targetLeg = params.new GTTOrderParams();
        targetLeg.transactionType = Constants.TRANSACTION_TYPE_SELL;
        targetLeg.quantity = record.getQty();
        targetLeg.orderType = Constants.ORDER_TYPE_LIMIT;
        targetLeg.product = Constants.PRODUCT_CNC;
        targetLeg.price = targetPrice;

        GTTParams.GTTOrderParams slLeg = params.new GTTOrderParams();
        slLeg.transactionType = Constants.TRANSACTION_TYPE_SELL;
        slLeg.quantity = record.getQty();
        slLeg.orderType = Constants.ORDER_TYPE_LIMIT;
        slLeg.product = Constants.PRODUCT_CNC;
        slLeg.price = slPrice;

        params.orders = List.of(targetLeg, slLeg); // index-aligned with triggerPrices above

        GTT result;
        try {
            result = kite.placeGTT(params);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GTT placement failed: " + e.getMessage());
        }

        gttRepository.save(new GttRecord(result.id, record.getOrderId(), userId, record.getSymbol(), targetPrice, slPrice));

        return new GttResponse(result.id, targetPrice, slPrice);
    }

    private Quote fetchQuote(KiteConnect kite, String symbol) {
        String key = Constants.EXCHANGE_NSE + ":" + symbol;
        try {
            return kite.getQuote(new String[]{key}).get(key);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to fetch quote: " + e.getMessage());
        }
    }

    private OrderRecord requireFilledOwnedOrder(String orderId, String userId) {
        OrderRecord record = orderStore.get(orderId)
                .filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown order_id."));

        if (!"COMPLETE".equals(record.getStatus()) || record.getAveragePrice() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not filled yet (status=" + record.getStatus() + ").");
        }
        return record;
    }

    private String requireLoggedIn(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first via POST /auth/login.");
        }
        return (String) userId;
    }
}
