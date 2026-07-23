package com.ayush.kite.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GttRepository extends JpaRepository<GttRecord, Integer> {

    Optional<GttRecord> findByOrderId(String orderId);

}
