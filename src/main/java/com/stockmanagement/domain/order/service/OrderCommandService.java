package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.lock.DistributedLock;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderItemCancelRequest;
import com.stockmanagement.domain.order.dto.OrderItemCancelResponse;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderItemResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderDeliverySnapshot;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.entity.OrderStatusHistory;
import com.stockmanagement.domain.order.repository.OrderDeliverySnapshotRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.entity.ProductVariant;
import com.stockmanagement.domain.product.repository.ProductVariantRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository historyRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final PaymentService paymentService;
    private final OutboxEventStore outboxEventStore;
    private final DeliveryAddressService deliveryAddressService;
    private final OrderDeliverySnapshotRepository deliverySnapshotRepository;

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

        List<Long> variantIds = request.getItems().stream()
                .map(OrderItemRequest::getVariantId)
                .toList();
        if (new HashSet<>(variantIds).size() != variantIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동일 변형이 중복으로 포함되어 있습니다. 수량을 합쳐서 제출해주세요.");
        }

        Map<Long, ProductVariant> variantMap = variantRepository.findAllByIdInWithProduct(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductVariant variant = Optional.ofNullable(variantMap.get(itemRequest.getVariantId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

            Product product = variant.getProduct();

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            if (variant.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_AVAILABLE);
            }

            if (variant.getPrice().compareTo(itemRequest.getUnitPrice()) != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("상품 '%s (%s)'의 단가가 일치하지 않습니다. (요청: %s, 현재: %s)",
                                product.getName(), variant.getOptionName(),
                                itemRequest.getUnitPrice(), variant.getPrice()));
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .variant(variant)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(variant.getPrice())
                    .build();

            items.add(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        // 3. 배송지 소유권 검증 + 스냅샷 데이터 확보
        var deliveryAddress = request.getDeliveryAddressId() != null
                ? deliveryAddressService.getById(request.getDeliveryAddressId(), userId)
                : null;

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

        // 5-1. 배송지 스냅샷 저장
        if (deliveryAddress != null) {
            deliverySnapshotRepository.save(OrderDeliverySnapshot.builder()
                    .orderId(savedOrder.getId())
                    .recipient(deliveryAddress.getRecipient())
                    .phone(deliveryAddress.getPhone())
                    .zipCode(deliveryAddress.getZipCode())
                    .address1(deliveryAddress.getAddress1())
                    .address2(deliveryAddress.getAddress2())
                    .build());
        }

        // 6. 재고 예약 (variantId 오름차순 — 데드락 방지)
        savedOrder.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getVariant().getId()))
                .forEach(item -> inventoryService.reserve(item.getVariant().getId(), item.getQuantity()));

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
            inventoryService.releaseReservation(item.getVariant().getId(), item.getQuantity());
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
            inventoryService.releaseReservation(item.getVariant().getId(), item.getQuantity());
        }

        couponService.releaseCoupon(order.getId());
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), previousStatus, OrderStatus.CANCELLED, "system:expiry");
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "SYSTEM_EXPIRY"));
    }

    // ===== 아이템 부분 취소 =====

    /**
     * 주문 아이템을 부분 취소한다.
     *
     * <p>CONFIRMED/PARTIAL_CANCELLED 상태 주문에서 지정 아이템을 취소하고 Toss 부분 환불을 수행한다.
     * 분산 락으로 동일 주문 동시 부분 취소를 방지한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>[Short TX] 검증 + 환불 금액 계산
     *   <li>[No TX] Toss 부분 환불 API 호출
     *   <li>[Short TX] 아이템 CANCELLED 처리 + 재고 해제 + 포인트 반환 + 주문 상태 전이
     * </ol>
     */
    @DistributedLock(key = "'order-item-cancel:' + #orderId")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderItemCancelResponse cancelItems(Long orderId, Long userId, boolean isAdmin,
                                               OrderItemCancelRequest request) {
        // 1. Short TX: 검증 + 환불 금액 계산
        CancelItemsContext ctx = validateAndPrepare(orderId, userId, isAdmin, request.getItemIds());

        // 2. Toss 부분 환불 (DB 커넥션 미점유)
        if (ctx.refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.cancelPartialForItems(orderId, ctx.refundAmount, request.getReason());
        }

        // 3. Short TX: 아이템 취소 + 재고 해제 + 포인트 반환 + 주문 상태 전이
        return applyItemCancel(orderId, request.getItemIds(), request.getReason(),
                ctx.refundAmount, ctx.pointsToRefund);
    }

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

        // 아이템 취소 + 재고 해제
        List<OrderItemResponse> cancelledResponses = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
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

    /** 부분 취소 검증 결과 컨텍스트. */
    record CancelItemsContext(BigDecimal refundAmount, long pointsToRefund) {}

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
