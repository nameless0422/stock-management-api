package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.entity.ProductVariant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * 상품 변형 응답 DTO.
 */
@Getter
@Builder
@Jacksonized
public class ProductVariantResponse {

    private final Long id;
    private final String optionName;
    private final String sku;
    private final BigDecimal price;
    private final ProductStatus status;

    public static ProductVariantResponse from(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .optionName(variant.getOptionName())
                .sku(variant.getSku())
                .price(variant.getPrice())
                .status(variant.getStatus())
                .build();
    }
}
