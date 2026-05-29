package com.stockmanagement.domain.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 변형 생성 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class ProductVariantCreateRequest {

    @NotBlank(message = "옵션 이름은 필수입니다.")
    @Size(max = 100, message = "옵션 이름은 100자 이하여야 합니다.")
    private String optionName;

    @NotBlank(message = "SKU는 필수입니다.")
    @Size(max = 100, message = "SKU는 100자 이하여야 합니다.")
    private String sku;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;
}
