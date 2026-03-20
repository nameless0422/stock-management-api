package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 조회 응답 DTO.
 *
 * <p>엔티티를 직접 반환하지 않고 필요한 필드만 추려 노출한다.
 * 엔티티 내부 구조가 바뀌어도 API 스펙이 바뀌지 않도록 분리한다.
 *
 * <p>{@link #from(Product)} 정적 팩토리로 생성한다.
 * {@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
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

    /** Product 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .category(product.getCategory())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
