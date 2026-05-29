package com.stockmanagement.domain.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 아이템 부분 취소 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class OrderItemCancelRequest {

    /** 취소할 주문 아이템 ID 목록 */
    @NotEmpty(message = "취소할 아이템을 하나 이상 선택해야 합니다.")
    private List<Long> itemIds;

    /** 취소 사유 (선택) */
    @Size(max = 255, message = "취소 사유는 255자 이내여야 합니다.")
    private String reason;
}
