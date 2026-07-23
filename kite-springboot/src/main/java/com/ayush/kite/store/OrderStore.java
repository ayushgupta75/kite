package com.ayush.kite.store;

import com.ayush.kite.client.KiteClientFactory;
import com.ayush.kite.orders.GttPricing;
import com.ayush.kite.orders.GttResponse;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.GTT;
import com.zerodhatech.models.GTTParams;
import com.zerodhatech.models.Quote;
import org.json.JSONException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OrderStore {

    private final OrderRepository orderRepository;
    private final KiteClientFactory kiteClientFactory;
//    private final OrderStore orderStore;
    private final GttRepository gttRepository;

    public OrderStore(OrderRepository orderRepository,KiteClientFactory kiteClientFactory,GttRepository gttRepository) {
        this.orderRepository = orderRepository;
        this.kiteClientFactory = kiteClientFactory;
        this.gttRepository = gttRepository;

    }

    public void seed(String orderId, String userId, String symbol, int qty) {
        orderRepository.save(new OrderRecord(orderId, userId, symbol, qty));
    }


    public void updateOrderState(Map<String, Object> payload) {

        String orderId = (String) payload.get("order_id");

        OrderRecord record = orderRepository.findById(orderId)
                                            .orElseGet(() -> new OrderRecord(orderId,
                                                        null,
                                                        (String) payload.get("tradingsymbol"),
                                                        toInt(payload.get("quantity"))));

        Double averagePrice = record.getAveragePrice();
        Object averagePriceRaw = payload.get("average_price");
        if (averagePriceRaw != null && !"0".equals(String.valueOf(averagePriceRaw)) && !"0.0".equals(String.valueOf(averagePriceRaw))) {
            averagePrice = Double.parseDouble(String.valueOf(averagePriceRaw));
        }

        record.update((String) payload.get("status"), averagePrice, (String) payload.get("status_message"));

        boolean filled = "COMPLETE".equals(record.getStatus()) && record.getAveragePrice() != null;
        boolean hasGttPercentages = record.getTargetPct() != null && record.getSlPct() != null;


        if (filled && hasGttPercentages) {
            placeGttForOrder(record, record.getTargetPct(), record.getSlPct());
       }

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

}
