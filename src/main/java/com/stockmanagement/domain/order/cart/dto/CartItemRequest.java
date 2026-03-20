package com.stockmanagement.domain.order.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장바구니 아이템 추가/수량 변경 요청 DTO. */
@Getter
@NoArgsConstructor
public class CartItemRequest {

    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private int quantity;
}
