package com.stockmanagement.domain.order.entity;

import com.stockmanagement.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 항목 엔티티.
 *
 * <p>하나의 {@link Order}에 속한 개별 상품 라인을 표현한다.
 *
 * <p>설계 포인트:
 * <ul>
 *   <li>{@code unitPrice}: 주문 당시 단가를 저장해 이후 상품 가격 변경에 영향을 받지 않는다.
 *   <li>{@code subtotal}: {@code unitPrice × quantity}로 생성 시 계산·저장한다.
 *   <li>{@code order}: 지연 로딩 — 항목만 단독 조회 시 불필요한 Order 쿼리를 방지한다.
 * </ul>
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 항목이 속한 주문 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 주문한 상품 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 주문 수량 */
    @Column(nullable = false)
    private int quantity;

    /** 주문 당시 단가 — 이후 상품 가격 변경과 무관하게 보존 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** 소계 = unitPrice × quantity */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OrderItem(Product product, int quantity, BigDecimal unitPrice) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 양방향 연관관계 동기화 — {@link Order#addItem(OrderItem)}에서만 호출한다.
     * 패키지 외부에서 직접 호출하지 않도록 package-private으로 제한한다.
     */
    void assignOrder(Order order) {
        this.order = order;
    }
}
