package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 조회 응답 DTO.
 *
 * <p>엔티티를 직접 반환하지 않고 필요한 필드만 추려 노출한다.
 * 엔티티 내부 구조가 바뀌어도 API 스펙이 바뀌지 않도록 분리한다.
 *
 * <p>생성자를 private으로 막고 {@link #from(Product)} 정적 팩토리만 노출해
 * 반드시 엔티티로부터 생성하도록 강제한다.
 */
@Getter
public class ProductResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String sku;
    private final String category;
    private final ProductStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private ProductResponse(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.sku = product.getSku();
        this.category = product.getCategory();
        this.status = product.getStatus();
        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
    }

    /** Product 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static ProductResponse from(Product product) {
        return new ProductResponse(product);
    }
}
