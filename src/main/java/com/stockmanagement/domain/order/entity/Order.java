package com.stockmanagement.domain.order.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문 엔티티.
 *
 * <p>한 번의 주문을 나타내며, 여러 {@link OrderItem}으로 구성된다.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>public setter 없음 — 상태 변경은 비즈니스 메서드({@link #cancel()})로만 수행
 *   <li>{@code userId}는 User 도메인 미구현으로 FK 없이 Long만 저장
 *   <li>{@code idempotencyKey}는 DB UNIQUE 제약으로 중복 주문을 방지
 *   <li>{@code totalAmount}는 생성 시 items 합계로 계산되며 이후 변경 불가
 * </ul>
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주문자 ID — User 도메인 미구현으로 FK 없이 저장 */
    @Column(nullable = false)
    private Long userId;

    /** 주문 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 주문 총액 — 생성 시 OrderItems 합계로 산출 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /**
     * 멱등성 키 — 클라이언트가 발급한 UUID.
     * 동일 키로 재요청이 오면 기존 주문을 그대로 반환한다.
     */
    @Column(nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    /**
     * 주문 항목 목록.
     * cascade = ALL: Order 저장/삭제 시 items도 함께 처리
     * orphanRemoval = true: items 컬렉션에서 제거되면 DB에서도 삭제
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    /** 선택된 배송지 ID — nullable (배송지 미선택 시 null) */
    @Column(name = "delivery_address_id")
    private Long deliveryAddressId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Order(Long userId, BigDecimal totalAmount, String idempotencyKey,
                  Long deliveryAddressId) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.idempotencyKey = idempotencyKey;
        this.deliveryAddressId = deliveryAddressId;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 주문을 취소한다.
     *
     * <p>PENDING 상태인 경우에만 취소 가능하다.
     * CONFIRMED·CANCELLED 상태에서 호출 시 {@link BusinessException}이 발생한다.
     *
     * @throws BusinessException 취소 불가 상태일 경우
     */
    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * Confirms the order after successful payment.
     * Called by the Payment domain upon payment approval.
     *
     * <p>Only a PENDING order can be confirmed.
     *
     * @throws BusinessException if the current status is not PENDING
     */
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Transitions a CONFIRMED order back to CANCELLED after payment refund.
     * Called by the Payment domain when a DONE payment is cancelled.
     *
     * <p>Different from {@link #cancel()} which handles pre-payment cancellation (PENDING → CANCELLED).
     *
     * @throws BusinessException if the current status is not CONFIRMED
     */
    public void refund() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 주문 항목을 추가한다.
     * OrderItem의 order 참조도 함께 설정(양방향 연관관계 동기화).
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }

    /** 읽기 전용 항목 목록 반환 */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
