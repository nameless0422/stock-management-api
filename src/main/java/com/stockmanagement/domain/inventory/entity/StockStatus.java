package com.stockmanagement.domain.inventory.entity;

/**
 * 재고 수량 노출 정책용 enum.
 *
 * <p>경쟁사에 정확한 재고 수량을 노출하지 않기 위해
 * 단계별 상태로 변환하여 공개 API에서 사용한다.
 */
public enum StockStatus {
    /** 충분한 재고 (임계값 초과) */
    IN_STOCK,
    /** 저재고 (1 이상 ~ 임계값 이하) */
    LOW_STOCK,
    /** 품절 (0 이하) */
    OUT_OF_STOCK;

    /**
     * 가용 재고 수량과 저재고 임계값으로 StockStatus를 결정한다.
     *
     * @param available          가용 재고 수량
     * @param lowStockThreshold  저재고 임계값 (SystemSetting에서 조회)
     */
    public static StockStatus of(int available, int lowStockThreshold) {
        if (available <= 0) return OUT_OF_STOCK;
        if (available <= lowStockThreshold) return LOW_STOCK;
        return IN_STOCK;
    }
}
