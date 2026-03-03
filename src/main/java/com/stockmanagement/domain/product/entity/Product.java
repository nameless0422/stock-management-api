package com.stockmanagement.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 마스터 엔티티.
 *
 * <p>재고(Inventory), 주문(Order) 등 다른 도메인에서 참조하는 핵심 마스터 데이터다.
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>외부에서 직접 필드를 수정하지 못하도록 setter를 열지 않는다.
 *   <li>상태 변경은 {@link #update}, {@link #changeStatus} 같은 의미 있는 메서드로만 허용한다.
 *   <li>JPA 프록시 생성을 위해 기본 생성자는 PROTECTED로 제한한다.
 * </ul>
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name", "sku", "status"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 상품 상세 설명 — 길이 제한 없음 (TEXT 타입) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 판매가 — 소수점 2자리까지 허용 (최대 12자리) */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Stock Keeping Unit — 상품을 고유하게 식별하는 관리 코드 */
    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(length = 100)
    private String category;

    /** 상품 판매 상태 — DB에 문자열로 저장 (예: "ACTIVE") */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /** 최초 생성 시각 — Hibernate가 자동 설정, 이후 변경 불가 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막 수정 시각 — Hibernate가 UPDATE 시 자동 갱신 */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 상품 생성 빌더.
     * status는 항상 ACTIVE로 초기화한다 — 등록 즉시 판매 가능 상태.
     */
    @Builder
    private Product(String name, String description, BigDecimal price,
                    String sku, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.sku = sku;
        this.category = category;
        this.status = ProductStatus.ACTIVE;
    }

    /**
     * 상품 정보를 수정한다.
     * SKU는 변경 불가 — 다른 시스템과 연동된 식별자이므로 불변으로 유지한다.
     */
    public void update(String name, String description, BigDecimal price, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    /**
     * 상품 판매 상태를 변경한다.
     * 삭제(DELETE) 대신 DISCONTINUED 상태로 전환해 이력을 보존한다.
     */
    public void changeStatus(ProductStatus status) {
        this.status = status;
    }
}
