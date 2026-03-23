package com.stockmanagement.common.event;

import lombok.Getter;

import java.math.BigDecimal;

/** 결제 승인 완료 이벤트. */
@Getter
public class PaymentConfirmedEvent extends DomainEvent {

    private final Long paymentId;
    private final Long orderId;
    private final BigDecimal amount;

    public PaymentConfirmedEvent(Long paymentId, Long orderId, BigDecimal amount) {
        super();
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
    }
}
