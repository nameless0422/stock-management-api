package com.stockmanagement.domain.product.wishlist.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wishlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private WishlistItem(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
