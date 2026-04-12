package com.stockmanagement.domain.order.cart.entity;

import com.stockmanagement.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 장바구니 아이템 엔티티.
 *
 * <p>사용자({@code userId})별로 상품({@code product})을 담는다.
 * {@code userId + product_id} 조합이 UNIQUE하므로 동일 상품은 수량만 변경된다.
 */
@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 장바구니 소유자 ID (users.id FK 없이 Long 저장) */
    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 담은 수량 — 1 이상이어야 한다 */
    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CartItem(Long userId, Product product, int quantity) {
        this.userId = userId;
        this.product = product;
        this.quantity = quantity;
    }

    /** 수량을 변경한다. */
    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }
}
