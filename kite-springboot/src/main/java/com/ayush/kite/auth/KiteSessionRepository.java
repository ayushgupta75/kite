package com.ayush.kite.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KiteSessionRepository extends JpaRepository<KiteSession, Long> {

    Optional<KiteSession> findByUserId(String userId);
}
