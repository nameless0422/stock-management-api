package com.stockmanagement.domain.inventory.entity;

/**
 * 재고 가용 수량 기준 상태 분류.
 * available = onHand - reserved - allocated
 */
public enum InventoryStatus {
    /** 정상 재고: available >= 10 */
    IN_STOCK,
    /** 저재고: 0 < available < 10 */
    LOW_STOCK,
    /** 품절: available <= 0 */
    OUT_OF_STOCK
}
