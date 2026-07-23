package com.ayush.kite.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderRecord, String> {

//    Optional<GttRecord> findByOrderId(String orderId);

}
