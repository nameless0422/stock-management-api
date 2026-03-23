package com.stockmanagement.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트 리스너.
 *
 * <p>이벤트는 트랜잭션 COMMIT 이후에 별도 스레드(eventTaskExecutor)에서 처리된다.
 * 리스너 실패가 원본 트랜잭션에 영향을 주지 않으며, 알림·외부 연동 등 부가 처리를 담당한다.
 *
 * <p>현재 역할: 구조화된 로그 출력.
 * 향후 확장 포인트: 이메일/SMS 알림, 메시지 브로커(Kafka/RabbitMQ) 발행, 통계 업데이트.
 */
@Slf4j
@Component
public class DomainEventListener {

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[Event] OrderCreated orderId={} userId={} amount={} at={}",
                event.getOrderId(), event.getUserId(), event.getTotalAmount(), event.getOccurredAt());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("[Event] OrderCancelled orderId={} userId={} reason={} at={}",
                event.getOrderId(), event.getUserId(), event.getReason(), event.getOccurredAt());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("[Event] PaymentConfirmed paymentId={} orderId={} amount={} at={}",
                event.getPaymentId(), event.getOrderId(), event.getAmount(), event.getOccurredAt());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        log.warn("[Event] LowStock productId={} productName={} available={} threshold={} at={}",
                event.getProductId(), event.getProductName(), event.getAvailable(),
                LowStockEvent.THRESHOLD, event.getOccurredAt());
    }
}
