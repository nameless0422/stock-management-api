package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 상태 변경 요청 DTO */
@Getter
@NoArgsConstructor
public class ProductStatusRequest {

    @NotNull(message = "상태 값은 필수입니다.")
    private ProductStatus status;
}
