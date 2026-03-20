package com.stockmanagement.domain.shipment.repository;

import com.stockmanagement.domain.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 배송 레포지토리. */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
