package com.stockmanagement.domain.order.dto;

import jakarta.validation.constraints.Size;

/** 주문 취소 요청 DTO — reason 미입력 허용 */
public record OrderCancelRequest(
        @Size(max = 255, message = "취소 사유는 255자를 초과할 수 없습니다.")
        String reason
) {}
