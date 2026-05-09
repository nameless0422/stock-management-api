package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.entity.OrderStatusHistory;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문-결제 연동 서비스.
 *
 * <p>결제 확정/환불/결제 상태 복원 등 Payment 도메인에서 호출되는 주문 상태 전이를 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final PointService pointService;
    private final OutboxEventStore outboxEventStore;
    private final OrderStatusHistoryRepository historyRepository;

    /**
     * 결제 완료 후 주문을 확정한다 — Payment 도메인에서 호출.
     *
     * <p>PENDING/PAYMENT_IN_PROGRESS → CONFIRMED 전환 및 재고 reserved → allocated 이동.
     *
     * @param id 확정할 주문 ID
     * @return 확정된 Order 엔티티
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public Order confirm(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus previousStatus = order.getStatus();
        order.confirm();

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmAllocation(item.getProduct().getId(), item.getQuantity());
        }

        recordHistory(order.getId(), previousStatus, OrderStatus.CONFIRMED, null);
        return order;
    }

    /**
     * 결제 취소 후 확정된 주문을 환불 처리한다 — Payment 도메인에서 호출.
     *
     * <p>CONFIRMED → CANCELLED 전환, allocated 재고 해제, 쿠폰/포인트 반환.
     *
     * @param id 환불할 주문 ID
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public void refund(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.refund();

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseAllocation(item.getProduct().getId(), item.getQuantity());
        }

        couponService.releaseCoupon(order.getId());
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), OrderStatus.CONFIRMED, OrderStatus.CANCELLED, null);
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "PAYMENT_REFUNDED"));
    }

    /**
     * 결제 오류로 묶인 PAYMENT_IN_PROGRESS 주문을 PENDING으로 복원한다 (스케줄러 전용).
     *
     * @param id 복원할 주문 ID
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public void resetPaymentInProgressBySystem(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.resetPaymentFailed();
        recordHistory(order.getId(), OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING, "system:stuck-payment-reset");
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }

    private void recordHistory(Long orderId, OrderStatus from, OrderStatus to, String note) {
        historyRepository.save(
                OrderStatusHistory.builder()
                        .orderId(orderId)
                        .fromStatus(from)
                        .toStatus(to)
                        .changedBy(currentUser())
                        .note(note)
                        .build()
        );
    }
}
