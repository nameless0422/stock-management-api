package com.stockmanagement.domain.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 수정 요청 DTO.
 *
 * <p>수정 가능 항목: 이름, 설명, 가격, 카테고리.
 * SKU는 다른 시스템의 식별자로 사용될 수 있으므로 수정 대상에서 제외한다.
 */
@Getter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    private String name;

    /** 선택 입력 — null 전달 시 기존 설명이 null로 갱신됨 */
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;

    /** 카테고리 ID — null이면 미분류로 변경 */
    private Long categoryId;
}
