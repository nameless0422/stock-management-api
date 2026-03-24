package com.stockmanagement.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 생성 요청 DTO.
 *
 * <p>필드:
 * <ul>
 *   <li>{@code userId}: 주문자 ID (User 도메인 미구현 — 임시 입력)
 *   <li>{@code idempotencyKey}: 클라이언트 발급 UUID. 동일 키 재전송 시 기존 주문 반환
 *   <li>{@code items}: 1개 이상의 주문 항목
 * </ul>
 */
@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    /** 주문자 ID */
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    /**
     * 멱등성 키 — 클라이언트가 UUID 등으로 직접 발급.
     * 같은 키로 재요청하면 새 주문을 만들지 않고 기존 주문을 반환한다.
     */
    @NotBlank(message = "멱등성 키는 필수입니다.")
    @Size(max = 100, message = "멱등성 키는 100자 이하여야 합니다.")
    private String idempotencyKey;

    /** 배송지 ID — 선택 항목. null이면 배송지 미지정 */
    private Long deliveryAddressId;

    /** 주문 항목 목록 — 최소 1개 이상 */
    @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> items;

    /** 쿠폰 코드 — 선택 항목. null이면 쿠폰 미적용 */
    @Size(max = 50, message = "쿠폰 코드는 50자 이하여야 합니다.")
    private String couponCode;

    /** 사용할 포인트 — 선택 항목. null 또는 0이면 포인트 미사용 */
    private Long usePoints;

    /** 장바구니 결제 전환 등 내부 호출용 팩토리 메서드. */
    public static OrderCreateRequest of(Long userId, String idempotencyKey,
                                        List<OrderItemRequest> items) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.userId = userId;
        req.idempotencyKey = idempotencyKey;
        req.items = items;
        return req;
    }
}
