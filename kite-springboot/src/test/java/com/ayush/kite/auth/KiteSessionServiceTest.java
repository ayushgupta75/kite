package com.ayush.kite.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KiteSessionServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KiteSessionRepository kiteSessionRepository;

    private KiteSessionService kiteSessionService;

    @BeforeEach
    void setUp() {
        this.kiteSessionService = new KiteSessionService(userRepository, kiteSessionRepository);
    }

    @Test
    void expiresAt_beforeFlushWindow_expiresSameDay() {
        ZonedDateTime login = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(6, 0), ZoneId.of("Asia/Kolkata"));
        Instant expiresAt = kiteSessionService.computeExpiresAt(login.toInstant());

        ZonedDateTime expected = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(7, 30), ZoneId.of("Asia/Kolkata"));
        assertThat(expiresAt).isEqualTo(expected.toInstant());
    }

    @Test
    void expiresAt_afterFlushWindow_expiresNextDay() {
        ZonedDateTime login = ZonedDateTime.of(LocalDate.of(2026, 7, 15), LocalTime.of(14, 0), ZoneId.of("Asia/Kolkata"));
        Instant expiresAt = kiteSessionService.computeExpiresAt(login.toInstant());

        ZonedDateTime expected = ZonedDateTime.of(LocalDate.of(2026, 7, 16), LocalTime.of(7, 30), ZoneId.of("Asia/Kolkata"));
        assertThat(expiresAt).isEqualTo(expected.toInstant());
    }

    @Test
    void saveSession_firstLogin_createsRow() {
        User user = userRepository.save(new User("ayush@example.com", "hash"));

        KiteSession session = kiteSessionService.saveSession(
                user.getId(), "AB1234", "access-token-1", "public-token-1", Instant.now());

        assertThat(session.getId()).isNotNull();
        assertThat(kiteSessionRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void saveSession_secondLogin_upsertsSameRow() {
        User user = userRepository.save(new User("ayush@example.com", "hash"));

        KiteSession first = kiteSessionService.saveSession(
                user.getId(), "AB1234", "access-token-1", "public-token-1", Instant.now());
        KiteSession second = kiteSessionService.saveSession(
                user.getId(), "AB1234", "access-token-2", "public-token-2", Instant.now());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getAccessToken()).isEqualTo("access-token-2");
        assertThat(kiteSessionRepository.count()).isEqualTo(1);
    }
}
