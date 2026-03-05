package com.stockmanagement.domain.inventory.entity;

import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 재고 엔티티.
 *
 * <p>상품(Product) 1개당 재고 레코드 1개가 존재한다 (1:1 관계).
 *
 * <p>재고 수량 모델:
 * <ul>
 *   <li>{@code onHand}    — 창고에 실제로 있는 물리적 재고
 *   <li>{@code reserved}  — 주문이 생성됐지만 아직 결제 완료되지 않아 잡아둔 재고
 *   <li>{@code allocated} — 결제 완료 후 출고 확정된 재고
 *   <li>{@code available} = onHand - reserved - allocated (계산값, DB 미저장)
 * </ul>
 *
 * <p>동시성 전략:
 * <ul>
 *   <li>{@code @Version} — 낙관적 락으로 동시 수정 충돌 감지
 *   <li>입고 등 고빈도 쓰기는 Repository에서 비관적 락 추가 적용
 * </ul>
 */
@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "onHand", "reserved", "allocated"})
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연관 상품 — 지연 로딩으로 불필요한 JOIN을 방지한다 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /** 창고 실물 재고 */
    @Column(nullable = false)
    private int onHand;

    /** 주문 생성 시 임시로 잡아둔 재고 (미결제 상태) */
    @Column(nullable = false)
    private int reserved;

    /** 결제 완료 후 출고 확정된 재고 */
    @Column(nullable = false)
    private int allocated;

    /**
     * 낙관적 락 버전 필드.
     * 두 트랜잭션이 동시에 같은 행을 수정하려 하면 나중에 커밋하는 쪽이 예외를 받는다.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 상품에 대한 초기 재고 레코드를 생성한다.
     * 최초 생성 시 모든 수량은 0으로 초기화된다.
     */
    @Builder
    private Inventory(Product product) {
        this.product = product;
        this.onHand = 0;
        this.reserved = 0;
        this.allocated = 0;
    }

    // ===== 계산 필드 =====

    /**
     * 현재 주문 가능한 수량을 반환한다.
     * {@code available = onHand - reserved - allocated}
     */
    public int getAvailable() {
        return onHand - reserved - allocated;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 입고 처리 — onHand를 증가시킨다.
     *
     * @param quantity 입고 수량 (양수여야 한다)
     */
    public void receive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("입고 수량은 0보다 커야 합니다.");
        }
        this.onHand += quantity;
    }

    /**
     * 주문 생성 시 재고를 예약한다 — reserved를 증가시킨다.
     * 가용 재고가 부족하면 {@link InsufficientStockException}을 발생시킨다.
     *
     * @param quantity 예약 수량
     */
    public void reserve(int quantity) {
        if (getAvailable() < quantity) {
            throw new InsufficientStockException(quantity, getAvailable());
        }
        this.reserved += quantity;
    }

    /**
     * 주문 취소 또는 결제 실패 시 예약을 해제한다 — reserved를 감소시킨다.
     *
     * @param quantity 해제 수량
     */
    public void releaseReservation(int quantity) {
        this.reserved = Math.max(0, this.reserved - quantity);
    }

    /**
     * 결제 완료 시 예약을 확정으로 전환한다.
     * reserved를 감소시키고 allocated를 증가시킨다.
     *
     * @param quantity 확정 수량
     */
    public void confirmAllocation(int quantity) {
        this.reserved = Math.max(0, this.reserved - quantity);
        this.allocated += quantity;
    }

    /**
     * Releases previously allocated stock after payment cancellation / refund.
     * Decreases {@code allocated} by the given quantity.
     * Called when a CONFIRMED order is refunded (payment was DONE → CANCELLED).
     *
     * @param quantity number of units to release from allocation
     */
    public void releaseAllocation(int quantity) {
        this.allocated = Math.max(0, this.allocated - quantity);
    }
}
