package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Outbox 이벤트 단건을 독립 트랜잭션으로 처리하는 컴포넌트.
 *
 * <p>{@code REQUIRES_NEW}를 사용하므로 각 이벤트는 별도 트랜잭션으로 커밋된다.
 * 특정 이벤트 처리 실패가 이미 성공한 이벤트를 롤백하지 않는다.
 *
 * <p>{@link OutboxEventRelayScheduler}에서만 호출된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * outboxId에 해당하는 이벤트를 독립 트랜잭션으로 발행한다.
     *
     * <p>이미 발행된 이벤트는 건너뛴다 (멱등성).
     * 발행 성공 시 {@code publishedAt}을 기록하고, 실패 시 {@code retryCount}를 증가시킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long outboxId) {
        OutboxEvent outbox = repository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getPublishedAt() != null) {
            return; // 삭제됐거나 이미 발행된 경우 (다른 인스턴스가 먼저 처리)
        }

        try {
            eventPublisher.publishEvent(buildEvent(outbox));
            outbox.markPublished();
        } catch (Exception e) {
            log.error("[Outbox] 이벤트 발행 실패 id={} type={} retry={} error={}",
                    outbox.getId(), outbox.getEventType(), outbox.getRetryCount(), e.getMessage());
            outbox.recordFailure();
        }
    }

    private Object buildEvent(OutboxEvent outbox) throws Exception {
        Map<String, Object> p = objectMapper.readValue(outbox.getPayload(), new TypeReference<>() {});
        return switch (outbox.getEventType()) {
            case ORDER_CREATED -> new OrderCreatedEvent(
                    toLong(p.get("orderId")), toLong(p.get("userId")), toBigDecimal(p.get("totalAmount")));
            case ORDER_CANCELLED -> new OrderCancelledEvent(
                    toLong(p.get("orderId")), toLong(p.get("userId")), (String) p.get("reason"));
            case PAYMENT_CONFIRMED -> new PaymentConfirmedEvent(
                    toLong(p.get("paymentId")), toLong(p.get("orderId")), toBigDecimal(p.get("amount")));
            case SHIPMENT_SHIPPED -> new ShipmentShippedEvent(
                    toLong(p.get("orderId")), (String) p.get("carrier"), (String) p.get("trackingNumber"));
            case SHIPMENT_DELIVERED -> new ShipmentDeliveredEvent(toLong(p.get("orderId")));
        };
    }

    private Long toLong(Object v) {
        if (v == null) throw new IllegalArgumentException("Outbox payload 필드 null — 이벤트 데이터 손상");
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) throw new IllegalArgumentException("Outbox payload 필드 null — 이벤트 데이터 손상");
        return v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
    }
}
