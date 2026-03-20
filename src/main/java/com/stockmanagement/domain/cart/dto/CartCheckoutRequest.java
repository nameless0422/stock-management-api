package com.stockmanagement.domain.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장바구니 → 주문 전환 요청 DTO. */
@Getter
@NoArgsConstructor
public class CartCheckoutRequest {

    /**
     * 멱등성 키 — 클라이언트가 UUID 등으로 발급.
     * 동일 키 재요청 시 새 주문을 생성하지 않고 기존 주문을 반환한다.
     */
    @NotBlank(message = "멱등성 키는 필수입니다.")
    @Size(max = 100, message = "멱등성 키는 100자 이하여야 합니다.")
    private String idempotencyKey;
}
