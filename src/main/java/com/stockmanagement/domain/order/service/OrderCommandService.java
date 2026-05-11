package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.entity.OrderStatusHistory;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 주문 생성·취소 커맨드 서비스.
 *
 * <p>주문 생성(create)과 취소(cancel, cancelBySystem, cancelPendingOrdersByUser)를 담당한다.
 * 재고 예약/해제, 쿠폰/포인트 적용/반환, Outbox 이벤트 발행을 포함한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository historyRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final OutboxEventStore outboxEventStore;
    private final DeliveryAddressService deliveryAddressService;

    /**
     * 주문을 생성한다 (내부 호출 전용 — 장바구니 결제 등).
     */
    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        return create(request, request.getUserId());
    }

    /**
     * 주문을 생성한다.
     *
     * <p>외부 API에서는 JWT에서 추출한 {@code userId}를 전달하여
     * 클라이언트가 임의로 userId를 지정하지 못하도록 한다.
     */
    @Transactional
    public OrderResponse create(OrderCreateRequest request, Long userId) {
        // 1. 멱등성 키 중복 확인
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        // 2. 각 항목 검증 및 OrderItem 목록 구성
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList();
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동일 상품이 중복으로 포함되어 있습니다. 수량을 합쳐서 제출해주세요.");
        }

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = Optional.ofNullable(productMap.get(itemRequest.getProductId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            if (product.getPrice().compareTo(itemRequest.getUnitPrice()) != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("상품 '%s'의 단가가 일치하지 않습니다. (요청: %s, 현재: %s)",
                                product.getName(), itemRequest.getUnitPrice(), product.getPrice()));
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            items.add(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        // 3. 배송지 소유권 검증
        if (request.getDeliveryAddressId() != null) {
            deliveryAddressService.getById(request.getDeliveryAddressId(), userId);
        }

        // 4. Order 생성
        long usePointsLong = request.getUsePoints() != null ? request.getUsePoints() : 0L;

        if (usePointsLong > 0) {
            pointService.validateBalance(userId, usePointsLong);
        }
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(totalAmount)
                .idempotencyKey(request.getIdempotencyKey())
                .deliveryAddressId(request.getDeliveryAddressId())
                .usedPoints(usePointsLong)
                .build();

        for (OrderItem item : items) {
            order.addItem(item);
        }

        // 5. Order 저장 (멱등성 키 경쟁 조건 대응)
        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
            orderRepository.flush();
        } catch (DataIntegrityViolationException e) {
            return orderRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .map(OrderResponse::from)
                    .orElseThrow(() -> e);
        }

        // 6. 재고 예약 (productId 오름차순 — 데드락 방지)
        savedOrder.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getProduct().getId()))
                .forEach(item -> inventoryService.reserve(item.getProduct().getId(), item.getQuantity()));

        // 7. 쿠폰 적용
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            var result = couponService.applyCoupon(
                    request.getCouponCode(), userId,
                    savedOrder.getId(), totalAmount);
            savedOrder.applyDiscount(result.getCouponId(), result.getDiscountAmount());
        }

        // 8. 포인트 차감
        if (usePointsLong > 0) {
            BigDecimal maxUsablePoints = totalAmount.subtract(savedOrder.getDiscountAmount());
            if (BigDecimal.valueOf(usePointsLong).compareTo(maxUsablePoints) > 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "포인트 사용 금액이 결제 가능 금액을 초과합니다. 최대 사용 가능 포인트: "
                                + maxUsablePoints.longValue() + "P");
            }
            pointService.use(savedOrder.getUserId(), usePointsLong, savedOrder.getId());
        }

        // 9. 상태 이력 기록
        recordHistory(savedOrder.getId(), null, OrderStatus.PENDING, null);

        // 10. 주문 생성 이벤트 저장 (Outbox)
        outboxEventStore.save(new OrderCreatedEvent(
                savedOrder.getId(), savedOrder.getUserId(), savedOrder.getTotalAmount()));

        return OrderResponse.from(savedOrder);
    }

    /**
     * 주문을 취소한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public OrderResponse cancel(Long id, Long userId, boolean isAdmin, String reason) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        validateOrderOwnership(order, userId, isAdmin);

        OrderStatus previousStatus = order.getStatus();
        order.cancel(reason);

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservation(item.getProduct().getId(), item.getQuantity());
        }

        couponService.releaseCoupon(order.getId());
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), previousStatus, OrderStatus.CANCELLED, null);
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "PENDING_CANCELLED"));
        return OrderResponse.from(order);
    }

    /**
     * 시스템(스케줄러)에 의한 자동 취소 — 소유권 검증 없음.
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public void cancelBySystem(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus previousStatus = order.getStatus();
        order.cancel(null);

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservation(item.getProduct().getId(), item.getQuantity());
        }

        couponService.releaseCoupon(order.getId());
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), previousStatus, OrderStatus.CANCELLED, "system:expiry");
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "SYSTEM_EXPIRY"));
    }

    /**
     * 회원 탈퇴 시 사용자의 모든 PENDING 주문을 강제 취소한다.
     */
    @Transactional
    public void cancelPendingOrdersByUser(Long userId) {
        orderRepository.findPendingIdsByUserId(userId).forEach(orderId -> {
            try {
                cancelBySystem(orderId);
            } catch (Exception e) {
                log.warn("[탈퇴] 주문 취소 실패 — orderId={}, reason={}", orderId, e.getMessage());
            }
        });
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
