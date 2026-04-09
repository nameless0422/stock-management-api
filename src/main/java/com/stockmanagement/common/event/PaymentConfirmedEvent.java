package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

/** 결제 승인 완료 이벤트. */
@Getter
public class PaymentConfirmedEvent extends DomainEvent implements OutboxSupport {

    private final Long paymentId;
    private final Long orderId;
    private final BigDecimal amount;

    public PaymentConfirmedEvent(Long paymentId, Long orderId, BigDecimal amount) {
        super();
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.PAYMENT_CONFIRMED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("paymentId", paymentId, "orderId", orderId, "amount", amount.toPlainString());
    }
}
