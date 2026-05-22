package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentReturnedEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.shipment.service.ShipmentService;
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
 *
 * <p>이벤트 처리 방식:
 * <ul>
 *   <li>알림·로그 이벤트 (ORDER_CREATED 등): Spring {@code ApplicationEventPublisher}로 발행 → 비동기 리스너 처리
 *   <li>후처리 재시도 이벤트 (SHIPMENT_CREATE, POINT_EARN): 서비스를 직접 호출 → 실패 시 retryCount 증가, 최대 5회 재시도
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
class OutboxEventProcessor {

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ShipmentService shipmentService;
    private final PointService pointService;

    /**
     * outboxId에 해당하는 이벤트를 독립 트랜잭션으로 발행한다.
     *
     * <p>이미 발행된 이벤트는 건너뛴다 (멱등성).
     * 발행 성공 시 {@code publishedAt}을 기록하고, 실패 시 {@code retryCount}를 증가시킨다.
     *
     * @return true = 발행 성공 또는 이미 발행됨, false = 발행 실패
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(Long outboxId) {
        OutboxEvent outbox = repository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getPublishedAt() != null) {
            return true; // 삭제됐거나 이미 발행된 경우 (다른 인스턴스가 먼저 처리)
        }

        try {
            dispatch(outbox);
            outbox.markPublished();
            return true;
        } catch (Exception e) {
            log.error("[Outbox] 이벤트 발행 실패 id={} type={} retry={} error={}",
                    outbox.getId(), outbox.getEventType(), outbox.getRetryCount(), e.getMessage());
            outbox.recordFailure();
            return false;
        }
    }

    /**
     * 이벤트 타입별로 처리를 분기한다.
     *
     * <ul>
     *   <li>SHIPMENT_CREATE, POINT_EARN: 서비스 직접 호출 (재시도 의미 있음)
     *   <li>나머지: ApplicationEventPublisher로 Spring 이벤트 발행
     * </ul>
     */
    private void dispatch(OutboxEvent outbox) throws Exception {
        Map<String, Object> p = objectMapper.readValue(outbox.getPayload(), new TypeReference<>() {});
        switch (outbox.getEventType()) {
            case SHIPMENT_CREATE -> {
                Long orderId = toLong(p.get("orderId"));
                try {
                    shipmentService.createForOrder(orderId);
                    log.info("[Outbox] 배송 레코드 생성 완료: orderId={}", orderId);
                } catch (com.stockmanagement.common.exception.BusinessException e) {
                    if (e.getErrorCode() == com.stockmanagement.common.exception.ErrorCode.SHIPMENT_ALREADY_EXISTS) {
                        // 이미 생성된 배송 — 멱등성 처리 (false dead letter 누적 방지)
                        log.warn("[Outbox] 배송 레코드 이미 존재, 중복 생성 스킵: orderId={}", orderId);
                        return;
                    }
                    throw e;
                }
            }
            case POINT_EARN -> {
                Long userId = toLong(p.get("userId"));
                long paidAmount = toLong(p.get("paidAmount"));
                Long orderId = toLong(p.get("orderId"));
                pointService.earn(userId, paidAmount, orderId);
                log.info("[Outbox] 포인트 적립 완료: userId={}, paidAmount={}, orderId={}", userId, paidAmount, orderId);
            }
            case SHIPMENT_DELIVERED -> {
                Long orderId = toLong(p.get("orderId"));
                // 배송 완료 시 PENDING 적립 포인트를 확정 (CONFIRMED + 잔액 반영)
                pointService.confirmPending(orderId);
                eventPublisher.publishEvent(new ShipmentDeliveredEvent(orderId));
                log.info("[Outbox] 배송 완료 처리 + 포인트 확정: orderId={}", orderId);
            }
            default -> eventPublisher.publishEvent(buildEvent(outbox, p));
        }
    }

    private Object buildEvent(OutboxEvent outbox, Map<String, Object> p) {
        return switch (outbox.getEventType()) {
            case ORDER_CREATED -> new OrderCreatedEvent(
                    toLong(p.get("orderId")), toLong(p.get("userId")), toBigDecimal(p.get("totalAmount")));
            case ORDER_CANCELLED -> new OrderCancelledEvent(
                    toLong(p.get("orderId")), toLong(p.get("userId")), (String) p.get("reason"));
            case PAYMENT_CONFIRMED -> new PaymentConfirmedEvent(
                    toLong(p.get("paymentId")), toLong(p.get("orderId")), toBigDecimal(p.get("amount")));
            case SHIPMENT_SHIPPED -> new ShipmentShippedEvent(
                    toLong(p.get("orderId")), (String) p.get("carrier"), (String) p.get("trackingNumber"));
            case SHIPMENT_RETURNED -> new ShipmentReturnedEvent(toLong(p.get("orderId")));
            default -> throw new IllegalStateException("buildEvent에서 처리되지 않은 타입: " + outbox.getEventType());
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
