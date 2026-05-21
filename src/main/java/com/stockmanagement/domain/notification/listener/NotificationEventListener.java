package com.stockmanagement.domain.notification.listener;

import com.stockmanagement.common.event.*;
import com.stockmanagement.domain.notification.entity.NotificationType;
import com.stockmanagement.domain.notification.service.NotificationService;
import com.stockmanagement.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 수신하여 인앱 알림을 생성하는 리스너.
 *
 * <p>AFTER_COMMIT 시점에 비동기로 실행되어 원본 트랜잭션에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final OrderRepository orderRepository;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        notificationService.create(
                event.getUserId(),
                NotificationType.ORDER_CREATED,
                "주문이 접수되었습니다",
                "주문번호 " + event.getOrderId() + " 주문이 정상적으로 접수되었습니다."
        );
        log.debug("[Notification] 주문 접수 알림 생성. orderId={}, userId={}", event.getOrderId(), event.getUserId());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.create(
                event.getUserId(),
                NotificationType.ORDER_CANCELLED,
                "주문이 취소되었습니다",
                "주문번호 " + event.getOrderId() + " 주문이 취소되었습니다."
        );
        log.debug("[Notification] 주문 취소 알림 생성. orderId={}, userId={}", event.getOrderId(), event.getUserId());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        orderRepository.findUserIdById(event.getOrderId()).ifPresent(userId -> {
            notificationService.create(
                    userId,
                    NotificationType.PAYMENT_CONFIRMED,
                    "결제가 완료되었습니다",
                    "주문번호 " + event.getOrderId() + " 결제가 정상적으로 완료되었습니다."
            );
            log.debug("[Notification] 결제 완료 알림 생성. orderId={}, userId={}", event.getOrderId(), userId);
        });
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentShipped(ShipmentShippedEvent event) {
        orderRepository.findUserIdById(event.getOrderId()).ifPresent(userId -> {
            notificationService.create(
                    userId,
                    NotificationType.SHIPMENT_SHIPPED,
                    "상품이 출고되었습니다",
                    "주문번호 " + event.getOrderId() + " 상품이 출고되었습니다. 운송장: " + event.getTrackingNumber()
            );
            log.debug("[Notification] 출고 알림 생성. orderId={}, userId={}", event.getOrderId(), userId);
        });
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentDelivered(ShipmentDeliveredEvent event) {
        orderRepository.findUserIdById(event.getOrderId()).ifPresent(userId -> {
            notificationService.create(
                    userId,
                    NotificationType.SHIPMENT_DELIVERED,
                    "배송이 완료되었습니다",
                    "주문번호 " + event.getOrderId() + " 배송이 완료되었습니다."
            );
            log.debug("[Notification] 배송 완료 알림 생성. orderId={}, userId={}", event.getOrderId(), userId);
        });
    }
}
