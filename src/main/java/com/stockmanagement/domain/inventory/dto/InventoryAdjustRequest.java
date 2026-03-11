package com.stockmanagement.domain.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 수동 조정 요청 DTO.
 *
 * <p>quantity가 양수이면 onHand 증가, 음수이면 감소한다.
 * 0은 허용되지 않으며 서비스 레이어에서 검증한다.
 */
@Getter
@NoArgsConstructor
public class InventoryAdjustRequest {

    @NotNull(message = "조정 수량은 필수입니다.")
    private Integer quantity;

    /** 조정 사유 (선택) */
    private String note;
}
