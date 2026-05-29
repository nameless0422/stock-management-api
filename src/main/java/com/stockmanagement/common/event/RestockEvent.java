package com.stockmanagement.common.event;

import lombok.Getter;

/** 재입고 이벤트 — available이 0에서 양수로 전환될 때 발행된다. */
@Getter
public class RestockEvent extends DomainEvent {

    private final Long productId;
    private final String productName;
    private final Long variantId;
    private final String variantOptionName;
    private final int available;

    public RestockEvent(Long productId, String productName,
                        Long variantId, String variantOptionName, int available) {
        super();
        this.productId = productId;
        this.productName = productName;
        this.variantId = variantId;
        this.variantOptionName = variantOptionName;
        this.available = available;
    }
}
