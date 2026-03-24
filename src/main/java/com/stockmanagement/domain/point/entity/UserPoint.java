package com.stockmanagement.domain.point.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자별 포인트 잔액 엔티티.
 *
 * <p>잔액 변경은 비관적 락(SELECT FOR UPDATE) 획득 후에만 수행한다.
 */
@Entity
@Table(name = "user_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserPoint(Long userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    /** 포인트를 적립한다. */
    public void earn(long amount) {
        this.balance += amount;
    }

    /** 포인트를 차감한다. 잔액 부족 시 예외를 발생시킨다. */
    public void use(long amount) {
        if (this.balance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }
        this.balance -= amount;
    }

    /** 반환된 포인트를 잔액에 더한다 (취소/환불 시). */
    public void refund(long amount) {
        this.balance += amount;
    }
}
