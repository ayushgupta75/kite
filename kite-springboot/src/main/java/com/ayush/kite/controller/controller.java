package com.ayush.kite.controller;

import com.ayush.kite.auth.KiteSessionService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import jakarta.servlet.http.HttpSession;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
public class controller {

    @Value("${kite.api-key}")
    private String apiKey;

    @Value("${kite.api-secret}")
    private String apiSecret;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final KiteSessionService kiteSessionService;

    public controller(KiteSessionService kiteSessionService) {
        this.kiteSessionService = kiteSessionService;
    }

    @GetMapping("/health")
    public String hello(){
        return "I am good";
    }

    @GetMapping("/login")
    public ResponseEntity<Void> kiteLogin(HttpSession session) {
        String userId = requireLoggedIn(session);

        KiteConnect kiteSdk = new KiteConnect(apiKey);

        // Kite echoes redirect_params back onto the /callback redirect, so we
        // can round-trip which of our own users this login belongs to.
        String redirectParams = URLEncoder.encode("user_id=" + userId, StandardCharsets.UTF_8);
        String loginUrl = kiteSdk.getLoginURL() + "&redirect_params=" + redirectParams;

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(loginUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> getRequestToken(
            @RequestParam("request_token") String requestToken,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam("user_id") String userId) {

        if (status != null && !status.equals("success")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kite login did not succeed (status=" + status + ")");
        }

        User user;
        try (KiteConnect kiteSdk = new KiteConnect(apiKey)) {
            user = kiteSdk.generateSession(requestToken, apiSecret);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login failed: " + e.getMessage());
        }

        kiteSessionService.saveSession(userId, user.userId, user.accessToken, user.publicToken, Instant.now());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/dashboard"))
                .build();
    }

    private String requireLoggedIn(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first via POST /auth/login.");
        }
        return (String) userId;
    }

}
