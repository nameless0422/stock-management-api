package com.stockmanagement.domain.inventory.dto;

import com.stockmanagement.domain.inventory.entity.Inventory;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/**
 * 재고 조회 응답 DTO.
 *
 * <p>클라이언트에 노출할 필드만 포함한다.
 * {@code available}은 DB에 저장되지 않는 계산값이지만, 응답에 포함해 클라이언트가
 * 별도로 계산하지 않아도 되도록 한다.
 *
 * <p>{@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
public class InventoryResponse {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final String category;

    /** 창고 실물 재고 */
    private final int onHand;

    /** 주문 생성으로 잡아둔 재고 (미결제) */
    private final int reserved;

    /** 결제 완료 후 출고 확정된 재고 */
    private final int allocated;

    /** 현재 주문 가능한 수량 = onHand - reserved - allocated */
    private final int available;

    private final LocalDateTime updatedAt;

    /** Inventory 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static InventoryResponse from(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .category(inventory.getProduct().getCategory())
                .onHand(inventory.getOnHand())
                .reserved(inventory.getReserved())
                .allocated(inventory.getAllocated())
                .available(inventory.getAvailable())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
