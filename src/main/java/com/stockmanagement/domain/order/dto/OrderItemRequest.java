package com.stockmanagement.domain.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 항목 요청 DTO.
 *
 * <p>주문 생성 요청({@link OrderCreateRequest}) 내 items 리스트의 각 항목을 나타낸다.
 *
 * <p>{@code unitPrice}: 클라이언트가 직접 입력한 단가.
 * OrderService에서 Product.price와 일치 여부를 검증한다.
 */
@Getter
@NoArgsConstructor
public class OrderItemRequest {

    /** 주문할 상품 ID */
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    /** 주문 수량 — 1 이상 10,000 이하 */
    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    @Max(value = 10000, message = "수량은 10,000 이하여야 합니다.")
    private int quantity;

    /**
     * 주문 당시 단가.
     * 서버에서 Product.price와 일치 여부를 검증한다.
     */
    @NotNull(message = "단가는 필수입니다.")
    @DecimalMin(value = "0.01", message = "단가는 0보다 커야 합니다.")
    private BigDecimal unitPrice;

    /** 장바구니 결제 전환 등 내부 호출용 팩토리 메서드. */
    public static OrderItemRequest of(Long productId, int quantity, BigDecimal unitPrice) {
        OrderItemRequest req = new OrderItemRequest();
        req.productId = productId;
        req.quantity = quantity;
        req.unitPrice = unitPrice;
        return req;
    }
}
