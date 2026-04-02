package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.lock.DistributedLock;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Outbox 테이블의 미발행 이벤트를 주기적으로 읽어 Spring ApplicationEventPublisher로 relay한다.
 *
 * <p>relay는 트랜잭션 안에서 수행되므로, 기존 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 리스너들이 커밋 후 정상 실행된다.
 *
 * <p>{@code outbox.relay.enabled=true} 환경에서만 빈이 생성된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
public class OutboxEventRelayScheduler {

    /** 재시도 최대 횟수 — 초과 시 영구 제외. */
    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("outbox.dead_letters", repository,
                        r -> r.countByPublishedAtIsNullAndRetryCountGreaterThanEqual(MAX_RETRY))
                .description("MAX_RETRY 초과로 영구 발행 실패된 Outbox 이벤트 수")
                .register(meterRegistry);
    }

    // 멀티 인스턴스 환경에서 동일 이벤트 중복 처리를 방지하기 위한 분산 락.
    // skipOnFailure=true: 다른 인스턴스가 실행 중이면 이번 주기를 조용히 건너뛴다.
    // leaseTime=30s: 100건 relay가 완료되기 충분한 시간.
    @DistributedLock(key = "'outbox:relay'", waitTime = 0, leaseTime = 30, skipOnFailure = true)
    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:5000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = repository
                .findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(MAX_RETRY);

        if (pending.isEmpty()) return;

        log.debug("[Outbox] relay 대상: {}건", pending.size());

        for (OutboxEvent outbox : pending) {
            try {
                eventPublisher.publishEvent(buildEvent(outbox));
                outbox.markPublished();
            } catch (Exception e) {
                log.error("[Outbox] 이벤트 발행 실패 id={} type={} retry={} error={}",
                        outbox.getId(), outbox.getEventType(), outbox.getRetryCount(), e.getMessage());
                outbox.recordFailure();
            }
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
