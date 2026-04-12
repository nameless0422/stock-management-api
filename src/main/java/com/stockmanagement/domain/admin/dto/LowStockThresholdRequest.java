package com.stockmanagement.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 저재고 임계값 변경 요청 DTO.
 *
 * <p>available {@literal <} threshold 인 상품을 대시보드 저재고 목록에 표시한다.
 */
public record LowStockThresholdRequest(
        @Min(value = 1, message = "임계값은 1 이상이어야 합니다.")
        @Max(value = 100000, message = "임계값은 100,000 이하여야 합니다.")
        int threshold
) {
}
