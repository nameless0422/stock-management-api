package com.stockmanagement.domain.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입고 요청 DTO.
 *
 * <p>입고(receive)는 창고에 실물 재고가 들어올 때 호출한다.
 * onHand 수량이 증가하며, 가용(available) 재고도 함께 늘어난다.
 */
@Getter
@NoArgsConstructor
public class InventoryReceiveRequest {

    @NotNull(message = "입고 수량은 필수입니다.")
    @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    /** 입고 사유 또는 메모 (선택) — 추후 이력 관리 시 활용 */
    private String note;
}
