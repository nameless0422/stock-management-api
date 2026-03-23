package com.stockmanagement.common.event;

import lombok.Getter;

/** 재고 부족 경보 이벤트 — available이 임계값 미만으로 떨어질 때 발행된다. */
@Getter
public class LowStockEvent extends DomainEvent {

    public static final int THRESHOLD = 10;

    private final Long productId;
    private final String productName;
    private final int available;

    public LowStockEvent(Long productId, String productName, int available) {
        super();
        this.productId = productId;
        this.productName = productName;
        this.available = available;
    }
}
