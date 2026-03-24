package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 도메인 이벤트를 Outbox 테이블에 저장하는 서비스.
 *
 * <p>호출 측의 트랜잭션에 참여하여({@link Propagation#MANDATORY}) 비즈니스 로직과
 * 이벤트 저장을 하나의 원자적 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OrderCreatedEvent e) {
        doSave(OutboxEventType.ORDER_CREATED, Map.of(
                "orderId", e.getOrderId(),
                "userId", e.getUserId(),
                "totalAmount", e.getTotalAmount().toPlainString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OrderCancelledEvent e) {
        doSave(OutboxEventType.ORDER_CANCELLED, Map.of(
                "orderId", e.getOrderId(),
                "userId", e.getUserId(),
                "reason", e.getReason()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(PaymentConfirmedEvent e) {
        doSave(OutboxEventType.PAYMENT_CONFIRMED, Map.of(
                "paymentId", e.getPaymentId(),
                "orderId", e.getOrderId(),
                "amount", e.getAmount().toPlainString()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ShipmentShippedEvent e) {
        doSave(OutboxEventType.SHIPMENT_SHIPPED, Map.of(
                "orderId", e.getOrderId(),
                "carrier", e.getCarrier(),
                "trackingNumber", e.getTrackingNumber()
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ShipmentDeliveredEvent e) {
        doSave(OutboxEventType.SHIPMENT_DELIVERED, Map.of(
                "orderId", e.getOrderId()
        ));
    }

    private void doSave(OutboxEventType type, Map<String, Object> payload) {
        try {
            repository.save(OutboxEvent.builder()
                    .eventType(type)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox 이벤트 직렬화 실패: " + type, ex);
        }
    }
}
