package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 변형 수정 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class ProductVariantUpdateRequest {

    @Size(max = 100, message = "옵션 이름은 100자 이하여야 합니다.")
    private String optionName;

    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;

    private ProductStatus status;
}
