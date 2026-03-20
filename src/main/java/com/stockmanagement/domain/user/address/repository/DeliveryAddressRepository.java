package com.stockmanagement.domain.user.address.repository;

import com.stockmanagement.domain.user.address.entity.DeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 배송지 레포지토리. */
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {

    /** 사용자의 배송지 목록 — 기본 배송지 우선, 이후 최신순 */
    List<DeliveryAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    /** 사용자의 현재 기본 배송지 조회 */
    Optional<DeliveryAddress> findByUserIdAndIsDefaultTrue(Long userId);

    /** 배송지 소유권 확인 (서비스 레이어에서 권한 검사용) */
    boolean existsByIdAndUserId(Long id, Long userId);

    /** 사용자의 배송지 수 조회 (배송지 삭제 후 다른 배송지 자동 지정에 사용) */
    long countByUserId(Long userId);
}
