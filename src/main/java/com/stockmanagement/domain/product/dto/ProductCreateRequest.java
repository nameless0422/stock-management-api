package com.stockmanagement.domain.product.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 등록 요청 DTO.
 *
 * <p>Bean Validation 애노테이션으로 입력값을 검증한다.
 * 검증 실패 시 {@link GlobalExceptionHandler}가 400 응답으로 변환한다.
 */
@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    private String name;

    /** 선택 입력 — null 허용 */
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다.")
    private BigDecimal price;

    /** SKU는 등록 후 변경 불가이므로 신중하게 입력해야 한다. */
    @NotBlank(message = "SKU는 필수입니다.")
    @Size(max = 100, message = "SKU는 100자를 초과할 수 없습니다.")
    private String sku;

    /** 선택 입력 — null 허용 */
    @Size(max = 100, message = "카테고리는 100자를 초과할 수 없습니다.")
    private String category;
}
