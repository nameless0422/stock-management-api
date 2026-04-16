package com.stockmanagement.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 주문 금액 미리보기 요청 DTO. */
@Getter
@Setter
@NoArgsConstructor
public class OrderPreviewRequest {

    @NotNull
    @Size(min = 1, message = "주문 항목은 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> items;

    /** 쿠폰 코드 (선택) */
    private String couponCode;

    /** 사용 포인트 (선택, 0이면 미사용) */
    @Min(0)
    private long usePoints;

    /** 배송지 ID (선택 — 현재 배송비 미구현이므로 예약 필드) */
    private Long deliveryAddressId;
}
