package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.entity.OrderStatusHistory;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderSpecification;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 주문 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>재고 연동:
 * <ul>
 *   <li>주문 생성: 각 항목에 대해 {@link InventoryService#reserve(Long, int)} 호출
 *   <li>주문 취소: 각 항목에 대해 {@link InventoryService#releaseReservation(Long, int)} 호출
 *   <li>결제 완료: {@link #confirm(Long)} — Payment 도메인에서 호출
 * </ul>
 * 모두 하나의 트랜잭션 내에서 처리되므로, 재고 부족 예외 시 주문과 모든 예약이 함께 롤백된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository historyRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final OutboxEventStore outboxEventStore;
    private final DeliveryAddressService deliveryAddressService;
    private final ShipmentRepository shipmentRepository;

    /**
     * 주문을 생성한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>멱등성 키 확인 — 중복 요청이면 기존 주문 반환
     *   <li>각 항목의 상품 존재 및 단가 검증, OrderItem 목록과 totalAmount 계산
     *   <li>Order 생성 및 저장 (cascade로 OrderItems 함께 저장)
     *   <li>각 항목에 대해 재고 예약 ({@code inventoryService.reserve})
     * </ol>
     *
     * <p>재고 부족 시 {@link com.stockmanagement.common.exception.InsufficientStockException}이 발생하며
     * 전체 트랜잭션이 롤백된다 (모든 재고 예약 취소, Order INSERT 취소).
     *
     * @param request 주문 생성 요청
     * @return 생성된 주문 응답
     */
    /**
     * 주문을 생성한다 (내부 호출 전용 — 장바구니 결제 등).
     *
     * <p>외부 API 호출은 {@link #create(OrderCreateRequest, Long)}를 사용한다.
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
        // 1. 멱등성 키 중복 확인 — 기존 주문 반환 (새 주문 생성 없음)
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        // 2. 각 항목 검증 및 OrderItem 목록 구성
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 상품 ID 일괄 조회 — 루프마다 findById()를 호출하는 N+1 방지
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = Optional.ofNullable(productMap.get(itemRequest.getProductId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            // 상품 판매 상태 검증 — INACTIVE/DISCONTINUED 상품은 주문 불가
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            // 단가 검증 — 클라이언트가 제출한 가격이 현재 DB 가격과 일치하는지 확인
            // (가격이 바뀐 경우 사용자가 인지할 수 있도록 명시적 오류 반환)
            if (product.getPrice().compareTo(itemRequest.getUnitPrice()) != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("상품 '%s'의 단가가 일치하지 않습니다. (요청: %s, 현재: %s)",
                                product.getName(), itemRequest.getUnitPrice(), product.getPrice()));
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice()) // 클라이언트 값 불신: DB canonical 가격 사용
                    .build();

            items.add(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        // 3. 배송지 소유권 검증 — 타인의 배송지로 주문 생성 방지
        if (request.getDeliveryAddressId() != null) {
            deliveryAddressService.getById(request.getDeliveryAddressId(), userId);
        }

        // 4. Order 생성 (확정된 totalAmount 사용)
        long usePointsLong = request.getUsePoints() != null ? request.getUsePoints() : 0L;

        // 4-1. 포인트 잔액 사전 검증 (fail-fast)
        // 재고 예약·쿠폰 적용 등 뮤테이션 전에 검증하여 불필요한 롤백을 방지한다.
        // use()가 REQUIRED이므로 롤백 시 쿠폰·재고도 함께 되돌려지나, 조기 검증으로 DB 작업을 줄인다.
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

        // 5. Order 저장 (cascade로 OrderItems도 함께 저장)
        Order savedOrder = orderRepository.save(order);

        // 6. 재고 예약 (fail-fast: 재고 부족이 가장 빈번한 실패 원인이므로 쿠폰/포인트 처리 전에 검증)
        for (OrderItem item : savedOrder.getItems()) {
            inventoryService.reserve(item.getProduct().getId(), item.getQuantity());
        }

        // 7. 쿠폰 적용 — 쿠폰 코드가 있으면 할인 금액 계산 및 사용 기록
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            var result = couponService.applyCoupon(
                    request.getCouponCode(), userId,
                    savedOrder.getId(), totalAmount);
            savedOrder.applyDiscount(result.getCouponId(), result.getDiscountAmount());
        }

        // 8. 포인트 차감 (사용 포인트가 있으면)
        if (usePointsLong > 0) {
            pointService.use(savedOrder.getUserId(), usePointsLong, savedOrder.getId());
        }

        // 9. 상태 이력 기록 (최초 생성: fromStatus=null, toStatus=PENDING)
        recordHistory(savedOrder.getId(), null, OrderStatus.PENDING, null);

        // 10. 주문 생성 이벤트 저장 (Outbox — 같은 트랜잭션 안에서 원자적 저장)
        outboxEventStore.save(new OrderCreatedEvent(
                savedOrder.getId(), savedOrder.getUserId(), savedOrder.getTotalAmount()));

        return OrderResponse.from(savedOrder);
    }

    /**
     * 주문 단건을 조회한다.
     * 항목(items)과 상품 정보를 fetch join으로 함께 로딩한다.
     * 결과를 Redis에 캐싱한다 (TTL: 5분, 상태 변경 시 명시적 evict).
     */
    @Cacheable(cacheNames = "orders", key = "#id")
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    /**
     * 주문 단건을 소유권 검증 후 조회한다 (외부 API 전용).
     *
     * <p>캐시를 우회하여 DB에서 직접 조회하므로,
     * 캐시 히트로 인한 소유권 검증 누락 위험이 없다.
     * ADMIN은 모든 주문에 접근 가능하고, USER는 본인 주문만 조회할 수 있다.
     */
    public OrderResponse getByIdForUser(Long id, Long userId, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwnership(order, userId, isAdmin);
        ShipmentStatus shipmentStatus = shipmentRepository.findByOrderId(id)
                .map(s -> s.getStatus()).orElse(null);
        return OrderResponse.from(order, null, shipmentStatus);
    }

    /**
     * 주문 목록을 동적 조건으로 페이징 조회한다.
     *
     * <p>권한별 동작:
     * <ul>
     *   <li>ADMIN: request.userId로 특정 사용자 주문 조회 가능, null이면 전체 조회
     *   <li>USER: username으로 본인 userId를 조회해 강제 적용 (request.userId 무시)
     * </ul>
     *
     * @param username    현재 인증 사용자명
     * @param isAdmin     ADMIN 권한 여부
     * @param request     검색 조건 (status, userId, startDate, endDate)
     * @param pageable    페이징/정렬 정보
     */
    public Page<OrderResponse> getList(Long userId, boolean isAdmin,
                                       OrderSearchRequest request, Pageable pageable) {
        // USER: 본인 주문만 조회 — DTO를 직접 변경하지 않고 forceUserId로 Specification에 주입
        Long forceUserId = isAdmin ? null : userId;
        Page<Order> page = orderRepository.findAll(OrderSpecification.of(request, forceUserId), pageable);

        // 배송 상태 배치 조회 (N+1 방지)
        List<Long> orderIds = page.map(Order::getId).toList();
        Map<Long, ShipmentStatus> statusMap = shipmentRepository.findStatusMapByOrderIds(orderIds);

        return page.map(o -> OrderResponse.from(o, null, statusMap.get(o.getId())));
    }

    /**
     * 사용자 주문 목록을 커서 기반으로 최신순 조회한다.
     *
     * <p>오프셋 방식 {@link #getList}와 달리 COUNT 쿼리가 없어 대용량 이력 스크롤에 적합하다.
     * ADMIN이 아닌 경우 userId를 강제 적용하여 타인 주문 조회를 차단한다.
     *
     * @param userId  조회 대상 사용자 ID (USER는 본인, ADMIN은 원하는 userId 지정 가능)
     * @param isAdmin ADMIN 권한 여부
     * @param status  주문 상태 필터 (null이면 전체)
     * @param lastId  이전 페이지 마지막 항목 ID (첫 조회 시 null)
     * @param size    한 페이지 항목 수
     */
    public CursorPage<OrderResponse> getOrderScroll(Long userId, boolean isAdmin,
                                                     OrderStatus status, Long lastId, int size) {
        PageRequest limit = PageRequest.of(0, size + 1);
        List<Order> items = lastId == null
                ? orderRepository.findCursorByUserId(userId, status, limit)
                : orderRepository.findCursorByUserIdAfter(userId, status, lastId, limit);
        // 배송 상태 배치 조회 (N+1 방지)
        List<Long> orderIds = items.stream().map(Order::getId).toList();
        Map<Long, ShipmentStatus> statusMap = shipmentRepository.findStatusMapByOrderIds(orderIds);

        return CursorPage.of(
                items.stream().map(o -> OrderResponse.from(o, null, statusMap.get(o.getId()))).toList(),
                size,
                OrderResponse::getId);
    }

    /**
     * 특정 주문의 상태 변경 이력을 시간순으로 조회한다.
     *
     * <p>ADMIN은 모든 주문 이력에 접근 가능하고, USER는 본인 주문 이력만 조회할 수 있다.
     */
    public List<OrderStatusHistoryResponse> getHistory(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwnership(order, userId, isAdmin);
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream().map(OrderStatusHistoryResponse::from).toList();
    }

    /**
     * 주문을 취소한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>주문 조회 (항목 포함 fetch join)
     *   <li>{@link Order#cancel()} — PENDING 상태 검증 + CANCELLED 전환
     *   <li>각 항목에 대해 재고 예약 해제
     * </ol>
     *
     * @param id 취소할 주문 ID
     * @return 취소된 주문 응답
     * @throws BusinessException PENDING이 아닌 주문에 대해 취소 시도 시
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public OrderResponse cancel(Long id, Long userId, boolean isAdmin, String reason) {
        // 비관적 락: 만료 스케줄러(cancel)와 결제 확정(confirm)의 동시 실행으로 인한
        // 재고 상태 불일치(allocated 누수)를 방지한다.
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 요청자가 주문 소유자인지 검증 (ADMIN은 모든 주문 취소 가능)
        validateOrderOwnership(order, userId, isAdmin);

        // 상태 검증 + CANCELLED 전환 (PENDING이 아니면 INVALID_ORDER_STATUS 예외)
        order.cancel(reason);

        // 재고 예약 해제
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservation(item.getProduct().getId(), item.getQuantity());
        }

        // 쿠폰 사용 취소 (쿠폰 미사용 주문이면 no-op)
        couponService.releaseCoupon(order.getId());

        // 포인트 환불 (사용된 포인트 반환 + 적립금 회수)
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED, null);
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "PENDING_CANCELLED"));
        return OrderResponse.from(order);
    }

    /**
     * 시스템(스케줄러)에 의한 자동 취소 — userId 없이 호출되는 전용 진입점.
     *
     * <p>일반 {@link #cancel(Long, Long, boolean)}과 달리 소유권 검증을 수행하지 않는다.
     * userId가 null인 채로 cancel()을 호출하면 포인트 환불·쿠폰 반환 로직에서
     * NPE가 발생할 가능성이 있으므로 Order에서 직접 userId를 조회한다.
     *
     * @param id 취소할 주문 ID
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public void cancelBySystem(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.cancel(null);

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservation(item.getProduct().getId(), item.getQuantity());
        }

        couponService.releaseCoupon(order.getId());
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED, "system:expiry");
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "SYSTEM_EXPIRY"));
    }

    /**
     * 결제 완료 후 주문을 확정한다 — Payment 도메인에서 호출.
     *
     * <p>PENDING → CONFIRMED 전환 및 재고 reserved → allocated 이동.
     * 반환된 {@link Order} 객체를 재사용함으로써 호출자의 추가 DB 조회를 제거한다.
     *
     * @param id 확정할 주문 ID
     * @return 확정된 Order 엔티티
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public Order confirm(Long id) {
        Order order = orderRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 전환 전 상태를 캡처 — PAYMENT_IN_PROGRESS(일반 결제) 또는 PENDING(가상계좌 Webhook)
        OrderStatus previousStatus = order.getStatus();
        order.confirm();

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmAllocation(item.getProduct().getId(), item.getQuantity());
        }

        recordHistory(order.getId(), previousStatus, OrderStatus.CONFIRMED, null);
        return order;
    }

    /**
     * Refunds a confirmed order after payment cancellation – called by the Payment domain.
     *
     * <p>Different from {@link #cancel(Long)} which handles pre-payment cancellation:
     * <ul>
     *   <li>{@code cancel}  – PENDING → CANCELLED, releases {@code reserved} inventory
     *   <li>{@code refund}  – CONFIRMED → CANCELLED, releases {@code allocated} inventory
     * </ul>
     *
     * @param id order ID to refund
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

        // 쿠폰 사용 취소 (쿠폰 미사용 주문이면 no-op)
        couponService.releaseCoupon(order.getId());

        // 포인트 환불 (사용된 포인트 반환 + 적립금 회수)
        pointService.refundByOrder(order.getUserId(), order.getId());

        recordHistory(order.getId(), OrderStatus.CONFIRMED, OrderStatus.CANCELLED, null);
        outboxEventStore.save(new OrderCancelledEvent(order.getId(), order.getUserId(), "PAYMENT_REFUNDED"));
    }

    /**
     * 주문 금액을 미리보기 계산한다 (DB 쓰기 없음).
     *
     * <p>쿠폰·포인트를 적용한 최종 결제 금액과 적립 예정 포인트를 반환한다.
     * 실제 주문 생성·재고 예약 없이 순수 계산만 수행한다.
     */
    public OrderPreviewResponse preview(OrderPreviewRequest request, Long userId) {
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList();
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal originalAmount = BigDecimal.ZERO;
        for (OrderItemRequest item : request.getItems()) {
            Product product = Optional.ofNullable(productMap.get(item.getProductId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }
            originalAmount = originalAmount.add(product.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // 쿠폰 할인 계산 (검증만 — 사용 이력 미기록)
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            var validateReq = new com.stockmanagement.domain.coupon.dto.CouponValidateRequest(
                    request.getCouponCode(), originalAmount);
            couponDiscount = couponService.validate(userId, validateReq).getDiscountAmount();
        }

        // 포인트 잔액 검증 (차감은 하지 않음)
        long usePoints = request.getUsePoints();
        if (usePoints > 0) {
            pointService.validateBalance(userId, usePoints);
        }
        BigDecimal pointDiscount = BigDecimal.valueOf(usePoints);

        BigDecimal shippingFee = BigDecimal.ZERO; // 현재 무료배송 정책
        BigDecimal finalAmount = originalAmount.subtract(couponDiscount).subtract(pointDiscount).add(shippingFee);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) finalAmount = BigDecimal.ZERO;

        long earnablePoints = finalAmount.compareTo(BigDecimal.ZERO) > 0
                ? Math.max(1L, Math.round(finalAmount.doubleValue() * 0.01))
                : 0L;

        return OrderPreviewResponse.builder()
                .originalAmount(originalAmount)
                .couponDiscount(couponDiscount)
                .pointDiscount(pointDiscount)
                .shippingFee(shippingFee)
                .finalAmount(finalAmount)
                .earnablePoints(earnablePoints)
                .build();
    }

    // ===== 내부 헬퍼 =====

    /**
     * 주문 소유권을 검증한다.
     * ADMIN은 모든 주문에 접근 가능하고, USER는 본인 주문만 접근할 수 있다.
     *
     * @param userId 요청자 ID (ADMIN bypass 시 null 허용)
     * @throws BusinessException USER가 타인 주문에 접근 시 {@code ORDER_ACCESS_DENIED}
     */
    private void validateOrderOwnership(Order order, Long userId, boolean isAdmin) {
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    /** 현재 인증 컨텍스트의 사용자명을 반환한다. 인증 정보가 없으면 "system"을 반환한다. */
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
