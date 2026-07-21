package com.ayush.kite.client;

import com.ayush.kite.auth.KiteSession;
import com.ayush.kite.auth.KiteSessionService;
import com.zerodhatech.kiteconnect.KiteConnect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class KiteClientFactory {

    @Value("${kite.api-key}")
    private String apiKey;

    private final KiteSessionService kiteSessionService;

    public KiteClientFactory(KiteSessionService kiteSessionService) {
        this.kiteSessionService = kiteSessionService;
    }

    public KiteConnect forUser(String userId) {
        KiteSession session = kiteSessionService.getActiveSession(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to Kite. Visit /login first."));

        KiteConnect kite = new KiteConnect(apiKey);
        kite.setAccessToken(session.getAccessToken());
        return kite;
    }
}
