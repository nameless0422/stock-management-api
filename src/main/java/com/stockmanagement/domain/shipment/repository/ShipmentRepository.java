package com.stockmanagement.domain.shipment.repository;

import com.stockmanagement.domain.shipment.entity.Shipment;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 배송 레포지토리. */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    /** 주문 ID 목록에 대한 배송 정보를 배치 조회한다 (주문 목록 N+1 방지). */
    @Query("SELECT s FROM Shipment s WHERE s.orderId IN :orderIds")
    List<Shipment> findAllByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

    /** 주문 ID → 배송 상태 Map을 반환하는 기본 메서드. */
    default Map<Long, ShipmentStatus> findStatusMapByOrderIds(Collection<Long> orderIds) {
        return findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(Shipment::getOrderId, Shipment::getStatus));
    }

    /** 특정 사용자의 배송 목록을 최신순 페이징 조회한다. */
    @Query("SELECT s FROM Shipment s WHERE s.orderId IN (SELECT o.id FROM Order o WHERE o.userId = :userId) ORDER BY s.createdAt DESC")
    Page<Shipment> findByUserId(@Param("userId") Long userId, Pageable pageable);
}
