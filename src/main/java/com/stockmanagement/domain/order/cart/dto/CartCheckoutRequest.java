package com.stockmanagement.domain.order.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** 쿠폰 코드 — 선택 항목. null이면 쿠폰 미적용 */
    @Size(max = 50, message = "쿠폰 코드는 50자 이하여야 합니다.")
    private String couponCode;

    /** 사용할 포인트 — 선택 항목. null 또는 0이면 포인트 미사용 */
    @Min(value = 0, message = "사용 포인트는 0 이상이어야 합니다.")
    private Long usePoints;

    /** 배송지 ID — 선택 항목. null이면 배송지 미지정 */
    private Long deliveryAddressId;

    /** 선택 결제할 상품 ID 목록 — null이면 장바구니 전체 결제 */
    private List<Long> selectedProductIds;
}
