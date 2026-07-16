package com.ayush.kite.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per user, upserted on every /callback login rather than kept as history.
 */
@Entity
@Table(name = "kite_sessions")
public class KiteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "kite_user_id", nullable = false, length = 20)
    private String kiteUserId;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "public_token")
    private String publicToken;

    @Column(name = "logged_in_at", nullable = false)
    private Instant loggedInAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KiteSession() {
    }

    KiteSession(User user) {
        this.user = user;
    }

    void update(String kiteUserId, String accessToken, String publicToken, Instant loggedInAt, Instant expiresAt) {
        this.kiteUserId = kiteUserId;
        this.accessToken = accessToken;
        this.publicToken = publicToken;
        this.loggedInAt = loggedInAt;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getKiteUserId() {
        return kiteUserId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public Instant getLoggedInAt() {
        return loggedInAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
