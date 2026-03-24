package com.stockmanagement.domain.point.repository;

import com.stockmanagement.domain.point.entity.UserPoint;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserPointRepository extends JpaRepository<UserPoint, Long> {

    Optional<UserPoint> findByUserId(Long userId);

    /**
     * 비관적 락으로 포인트 잔액을 조회한다.
     * 잔액 변경(적립/사용/환불) 전 반드시 이 메서드를 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT up FROM UserPoint up WHERE up.userId = :userId")
    Optional<UserPoint> findByUserIdWithLock(@Param("userId") Long userId);
}
