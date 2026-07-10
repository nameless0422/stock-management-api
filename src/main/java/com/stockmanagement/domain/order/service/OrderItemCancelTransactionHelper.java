package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.order.dto.OrderItemCancelResponse;
import com.stockmanagement.domain.order.dto.OrderItemResponse;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 아이템 부분 취소의 DB 연산을 단기 트랜잭션으로 분리하는 헬퍼.
 *
 * <p>{@link OrderCommandService#cancelItems}는 Toss 부분 환불(외부 HTTP)을 사이에 두고
 * 검증([Short TX])과 적용([Short TX])을 실행한다. 같은 클래스 내부 호출(self-invocation)은
 * 프록시를 우회하여 {@code @Transactional}이 적용되지 않으므로 별도 빈으로 분리한다.
 * ({@code PaymentTransactionHelper}와 동일한 패턴)
 */
@Service
@RequiredArgsConstructor
class OrderItemCancelTransactionHelper {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository historyRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final OutboxEventStore outboxEventStore;

    /** 부분 취소 검증 결과 컨텍스트. */
    record CancelItemsContext(BigDecimal refundAmount, long pointsToRefund) {}

    /**
     * [Short TX] 부분 취소 검증 + 환불 금액 계산.
     */
    @Transactional
    CancelItemsContext validateAndPrepare(Long orderId, Long userId, boolean isAdmin,
                                          List<Long> itemIds) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        validateOrderOwnership(order, userId, isAdmin);

        // CONFIRMED 또는 PARTIAL_CANCELLED 상태만 부분 취소 가능
        if (order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.PARTIAL_CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        // 요청 아이템 검증
        Set<Long> requestedIds = new HashSet<>(itemIds);
        Map<Long, OrderItem> itemMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, i -> i));

        List<OrderItem> targetItems = new ArrayList<>();
        for (Long itemId : requestedIds) {
            OrderItem item = itemMap.get(itemId);
            if (item == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }
            if (!item.isActive()) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_ALREADY_CANCELLED);
            }
            targetItems.add(item);
        }

        // 환불 금액 계산 (비례 안분)
        BigDecimal cancelledSubtotal = targetItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cancelRatio = cancelledSubtotal.divide(
                order.getTotalAmount(), 10, RoundingMode.DOWN);

        BigDecimal proportionalDiscount = order.getDiscountAmount()
                .multiply(cancelRatio).setScale(0, RoundingMode.DOWN);

        long proportionalPoints = BigDecimal.valueOf(order.getUsedPoints())
                .multiply(cancelRatio).setScale(0, RoundingMode.DOWN).longValue();

        BigDecimal refundAmount = cancelledSubtotal
                .subtract(proportionalDiscount)
                .subtract(BigDecimal.valueOf(proportionalPoints))
                .max(BigDecimal.ZERO);

        return new CancelItemsContext(refundAmount, proportionalPoints);
    }

    /**
     * [Short TX] 아이템 취소 + 재고 해제 + 포인트 반환 + 주문 상태 전이.
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#orderId")
    OrderItemCancelResponse applyItemCancel(Long orderId, List<Long> itemIds, String reason,
                                            BigDecimal refundAmount, long pointsToRefund) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus previousStatus = order.getStatus();
        Set<Long> requestedIds = new HashSet<>(itemIds);

        // 아이템 취소 + 재고 해제 (variantId 오름차순 — 재고 락 획득 순서 통일, 데드락 방지)
        List<OrderItemResponse> cancelledResponses = new ArrayList<>();
        for (OrderItem item : order.getItemsSortedByVariant()) {
            if (requestedIds.contains(item.getId()) && item.isActive()) {
                item.cancel();
                inventoryService.releaseAllocation(item.getVariant().getId(), item.getQuantity());
                cancelledResponses.add(OrderItemResponse.from(item));
            }
        }

        // 비례 포인트 반환
        if (pointsToRefund > 0) {
            pointService.refundPartial(order.getUserId(), orderId, pointsToRefund);
        }

        // 주문 상태 전이
        order.partialCancel(reason);

        // 모든 아이템 취소 → 쿠폰 반환 + EARN 포인트 처리
        if (order.isAllItemsCancelled()) {
            couponService.releaseCoupon(orderId);
            pointService.expirePending(orderId);
        }

        recordHistory(orderId, previousStatus, order.getStatus(),
                "partial-cancel:items=" + itemIds);
        outboxEventStore.save(new OrderCancelledEvent(
                orderId, order.getUserId(), "PARTIAL_ITEM_CANCELLED"));

        return OrderItemCancelResponse.builder()
                .orderId(orderId)
                .orderStatus(order.getStatus().name())
                .refundAmount(refundAmount)
                .refundedPoints(pointsToRefund)
                .cancelledItems(cancelledResponses)
                .build();
    }

    private void validateOrderOwnership(Order order, Long userId, boolean isAdmin) {
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
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
