package com.ayush.kite.auth;

import java.time.Instant;

public record SessionStatusResponse(boolean loggedIn, String userId, boolean kiteConnected, Instant kiteExpiresAt) {
}
