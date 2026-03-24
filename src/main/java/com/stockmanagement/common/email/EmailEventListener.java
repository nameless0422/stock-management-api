package com.stockmanagement.common.email;

import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.event.PaymentConfirmedEvent;
import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트 → 이메일 발송 리스너.
 *
 * <p>트랜잭션 커밋 이후({@link TransactionPhase#AFTER_COMMIT}) 비동기로 실행된다.
 * 이메일 발송 실패는 로그로만 기록하며 원본 트랜잭션에 영향을 주지 않는다.
 *
 * <p>{@code mail.enabled=true} 환경에서만 빈이 생성된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class EmailEventListener {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Async("eventTaskExecutor")
    @Transactional(readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        orderRepository.findByIdWithItems(event.getOrderId()).ifPresentOrElse(
                order -> userRepository.findById(order.getUserId()).ifPresentOrElse(
                        user -> emailService.sendOrderCreated(user.getEmail(), order.getId(),
                                order.getTotalAmount(), order.getItems()),
                        () -> log.warn("[Mail] 주문 생성 이메일 스킵: 사용자 없음 orderId={}", event.getOrderId())
                ),
                () -> log.warn("[Mail] 주문 생성 이메일 스킵: 주문 없음 orderId={}", event.getOrderId())
        );
    }

    @Async("eventTaskExecutor")
    @Transactional(readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        userRepository.findById(event.getUserId()).ifPresentOrElse(
                user -> emailService.sendOrderCancelled(user.getEmail(), event.getOrderId(), event.getReason()),
                () -> log.warn("[Mail] 주문 취소 이메일 스킵: 사용자 없음 userId={}", event.getUserId())
        );
    }

    @Async("eventTaskExecutor")
    @Transactional(readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        var orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("[Mail] 결제 완료 이메일 스킵: 주문 없음 orderId={}", event.getOrderId());
            return;
        }
        userRepository.findById(orderOpt.get().getUserId()).ifPresentOrElse(
                user -> emailService.sendPaymentConfirmed(user.getEmail(), event.getOrderId(), event.getAmount()),
                () -> log.warn("[Mail] 결제 완료 이메일 스킵: 사용자 없음 orderId={}", event.getOrderId())
        );
    }

    @Async("eventTaskExecutor")
    @Transactional(readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentShipped(ShipmentShippedEvent event) {
        var orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("[Mail] 출고 이메일 스킵: 주문 없음 orderId={}", event.getOrderId());
            return;
        }
        userRepository.findById(orderOpt.get().getUserId()).ifPresentOrElse(
                user -> emailService.sendShipmentShipped(user.getEmail(), event.getOrderId(),
                        event.getCarrier(), event.getTrackingNumber()),
                () -> log.warn("[Mail] 출고 이메일 스킵: 사용자 없음 orderId={}", event.getOrderId())
        );
    }

    @Async("eventTaskExecutor")
    @Transactional(readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        var orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("[Mail] 배송 완료 이메일 스킵: 주문 없음 orderId={}", event.getOrderId());
            return;
        }
        userRepository.findById(orderOpt.get().getUserId()).ifPresentOrElse(
                user -> emailService.sendShipmentDelivered(user.getEmail(), event.getOrderId()),
                () -> log.warn("[Mail] 배송 완료 이메일 스킵: 사용자 없음 orderId={}", event.getOrderId())
        );
    }
}
