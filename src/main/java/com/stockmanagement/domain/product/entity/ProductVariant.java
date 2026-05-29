package com.stockmanagement.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 변형(variant) 엔티티.
 *
 * <p>하나의 {@link Product}에 속한 옵션 조합(색상·사이즈 등)을 나타낸다.
 * 각 variant는 고유 SKU, 개별 가격, 독립적인 재고(Inventory)를 갖는다.
 *
 * <p>옵션이 없는 상품도 "기본" variant 1개를 필수로 가진다.
 */
@Entity
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@BatchSize(size = 50)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 상품 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 옵션 이름 (예: "빨강/L", "기본") */
    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    /** variant 고유 SKU */
    @Column(nullable = false, length = 100, unique = true)
    private String sku;

    /** variant 개별 가격 */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    /** variant 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ProductVariant(Product product, String optionName, String sku,
                           BigDecimal price, ProductStatus status) {
        this.product = product;
        this.optionName = optionName;
        this.sku = sku;
        this.price = price;
        this.status = status != null ? status : ProductStatus.ACTIVE;
    }

    /** variant 정보를 수정한다. */
    public void update(String optionName, BigDecimal price) {
        if (optionName != null) this.optionName = optionName;
        if (price != null) this.price = price;
    }

    /** variant 상태를 변경한다. */
    public void changeStatus(ProductStatus status) {
        this.status = status;
    }
}
