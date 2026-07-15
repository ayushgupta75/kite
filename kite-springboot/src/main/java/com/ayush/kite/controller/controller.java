package com.ayush.kite.controller;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;

@RestController
public class controller {

    @Value("${kite.api-key}")
    private String apiKey;

    @Value("${kite.api-secret}")
    private String apiSecret;

//    public controller() {
//        apiKey = "";
//    }

//    public controller(String apiKey) {
//        this.apiKey = apiKey;
//    }


    @GetMapping("/health")
    public String hello(){
        return "I am good";
    }

    @GetMapping("/login")
    public ResponseEntity<Void> kiteLogin(){
        KiteConnect kiteSdk = new KiteConnect(apiKey);
        System.out.println("");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(kiteSdk.getLoginURL()))
                .build();

    }

    @GetMapping("/callback")
    public ResponseEntity<String> getRequestToken(
            @RequestParam("request_token") String requestToken,
            @RequestParam(value = "status", required = false) String status) {

        if (status != null && !status.equals("success")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kite login did not succeed (status=" + status + ")");
        }

        KiteConnect kiteSdk = new KiteConnect(apiKey);
        User user;
        try {
            user = kiteSdk.generateSession(requestToken, apiSecret);
        } catch (KiteException | JSONException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login failed: " + e.getMessage());
        }

        cacheAccessToken(user.accessToken);

        return ResponseEntity.ok("Logged in, access token cached.");
    }

}
