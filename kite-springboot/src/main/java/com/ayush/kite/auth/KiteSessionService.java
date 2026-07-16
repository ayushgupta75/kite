package com.ayush.kite.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class KiteSessionService {

    // Kite flushes access tokens sometime between 5:00-7:30 AM IST; a token
    // generated at/after 7:30 AM is valid until the next day's flush.
    // https://kite.trade/forum/discussion/3468/access-token-expiry-time-everyday
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime FLUSH_TIME = LocalTime.of(7, 30);

    private final UserRepository userRepository;
    private final KiteSessionRepository kiteSessionRepository;

    public KiteSessionService(UserRepository userRepository, KiteSessionRepository kiteSessionRepository) {
        this.userRepository = userRepository;
        this.kiteSessionRepository = kiteSessionRepository;
    }

    public Instant computeExpiresAt(Instant loginTime) {
        ZonedDateTime loginIst = loginTime.atZone(IST);
        ZonedDateTime todayFlush = loginIst.toLocalDate().atTime(FLUSH_TIME).atZone(IST);
        ZonedDateTime nextFlush = loginIst.isBefore(todayFlush) ? todayFlush : todayFlush.plusDays(1);
        return nextFlush.toInstant();
    }

    public KiteSession saveSession(String userId, String kiteUserId, String accessToken, String publicToken, Instant loginTime) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user_id=" + userId));

        Instant expiresAt = computeExpiresAt(loginTime);

        KiteSession session = kiteSessionRepository.findByUserId(userId)
                .orElseGet(() -> new KiteSession(user));
        session.update(kiteUserId, accessToken, publicToken, loginTime, expiresAt);

        return kiteSessionRepository.save(session);
    }

    public Optional<KiteSession> getActiveSession(String userId) {
        return kiteSessionRepository.findByUserId(userId)
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()));
    }
}
