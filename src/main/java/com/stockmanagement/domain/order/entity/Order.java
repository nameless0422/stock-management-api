package com.stockmanagement.domain.order.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import org.hibernate.annotations.BatchSize;
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
    @Column(nullable = false, precision = 15, scale = 2)
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
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    /** 선택된 배송지 ID — nullable (배송지 미선택 시 null) */
    @Column(name = "delivery_address_id")
    private Long deliveryAddressId;

    /** 적용된 쿠폰 ID — nullable (쿠폰 미사용 시 null) */
    @Column(name = "coupon_id")
    private Long couponId;

    /** 쿠폰 할인 금액 (기본 0). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    /** 주문 시 사용한 포인트 (기본 0). */
    @Column(nullable = false)
    private long usedPoints;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Order(Long userId, BigDecimal totalAmount, String idempotencyKey,
                  Long deliveryAddressId, Long couponId, BigDecimal discountAmount, long usedPoints) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.idempotencyKey = idempotencyKey;
        this.deliveryAddressId = deliveryAddressId;
        this.couponId = couponId;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.usedPoints = usedPoints;
    }

    // ===== 비즈니스 메서드 =====

    /** 취소 사유 — null이면 사유 미입력 */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /**
     * 주문을 취소한다.
     *
     * <p>PENDING 상태인 경우에만 취소 가능하다.
     * PAYMENT_IN_PROGRESS 상태에서는 Toss API 호출이 진행 중이므로 취소 불가.
     *
     * @param reason 취소 사유 (null 허용)
     * @throws BusinessException 취소 불가 상태일 경우
     */
    public void cancel(String reason) {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }

    /**
     * Toss API 호출 직전 결제 진행 중 상태로 전환한다.
     *
     * <p>PENDING → PAYMENT_IN_PROGRESS. 만료 스케줄러({@code findExpiredPendingOrderIds})가
     * PENDING만 조회하므로 이 상태로 전환된 주문은 자동 취소 대상에서 제외된다.
     *
     * @throws BusinessException PENDING이 아닌 상태에서 호출 시
     */
    public void startPayment() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.PAYMENT_IN_PROGRESS;
    }

    /**
     * 결제 실패·오류 시 PAYMENT_IN_PROGRESS → PENDING 으로 복원한다.
     *
     * <p>복원 후 만료 스케줄러가 다시 이 주문을 정리할 수 있다.
     * Toss API 오류 또는 비-DONE 응답 수신 시 {@link com.stockmanagement.domain.payment.service.PaymentTransactionHelper}가 호출한다.
     */
    public void resetPaymentFailed() {
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.PENDING;
    }

    /**
     * 결제 성공 후 주문을 확정한다 — Payment 도메인에서 호출.
     *
     * <p>PAYMENT_IN_PROGRESS(일반 결제) 또는 PENDING(가상계좌 Webhook) 상태에서 CONFIRMED로 전환.
     *
     * @throws BusinessException 전환 가능한 상태가 아닌 경우
     */
    public void confirm() {
        if (this.status != OrderStatus.PAYMENT_IN_PROGRESS && this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Toss 취소 API 호출 직전 CONFIRMED → CANCEL_IN_PROGRESS 로 전환한다.
     *
     * <p>이미 CANCEL_IN_PROGRESS이면 멱등적으로 무시한다 (재시도 안전).
     *
     * @throws BusinessException CONFIRMED 또는 CANCEL_IN_PROGRESS가 아닌 상태에서 호출 시
     */
    public void startCancellation() {
        if (this.status == OrderStatus.CANCEL_IN_PROGRESS) return; // 재시도 시 idempotent
        if (this.status != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CANCEL_IN_PROGRESS;
    }

    /**
     * Toss 오류 시 CANCEL_IN_PROGRESS → CONFIRMED 복원.
     *
     * @throws BusinessException CANCEL_IN_PROGRESS가 아닌 상태에서 호출 시
     */
    public void resetCancellationFailed() {
        if (this.status != OrderStatus.CANCEL_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Transitions a CONFIRMED or CANCEL_IN_PROGRESS order to CANCELLED after payment refund.
     * Called by the Payment domain when a DONE payment is cancelled.
     *
     * <p>Different from {@link #cancel()} which handles pre-payment cancellation (PENDING → CANCELLED).
     *
     * @throws BusinessException if the current status is not CONFIRMED or CANCEL_IN_PROGRESS
     */
    public void refund() {
        if (this.status != OrderStatus.CONFIRMED && this.status != OrderStatus.CANCEL_IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 실제 결제 금액을 계산한다 (= totalAmount - discountAmount - usedPoints, 최소 0).
     *
     * <p>쿠폰 할인과 포인트 사용을 차감한 금액으로, Toss 결제 요청에 사용된다.
     */
    public BigDecimal getPayableAmount() {
        BigDecimal payable = totalAmount
                .subtract(discountAmount)
                .subtract(BigDecimal.valueOf(usedPoints));
        return payable.compareTo(BigDecimal.ZERO) > 0 ? payable : BigDecimal.ZERO;
    }

    /**
     * 쿠폰 할인을 적용한다.
     * 저장 후 couponService.applyCoupon() 결과를 받아 호출한다.
     */
    public void applyDiscount(Long couponId, BigDecimal discountAmount) {
        this.couponId = couponId;
        this.discountAmount = discountAmount;
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
