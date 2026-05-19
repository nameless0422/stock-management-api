package com.stockmanagement.common.event;

import lombok.Getter;

/** 재입고 이벤트 — available이 0에서 양수로 전환될 때 발행된다. */
@Getter
public class RestockEvent extends DomainEvent {

    private final Long productId;
    private final String productName;
    private final int available;

    public RestockEvent(Long productId, String productName, int available) {
        super();
        this.productId = productId;
        this.productName = productName;
        this.available = available;
    }
}
