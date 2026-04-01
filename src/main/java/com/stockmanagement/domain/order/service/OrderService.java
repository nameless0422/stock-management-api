package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.user.address.service.DeliveryAddressService;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderDetailResponse;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.payment.dto.PaymentResponse;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
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
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.common.event.OrderCancelledEvent;
import com.stockmanagement.common.event.OrderCreatedEvent;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final UserRepository userRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final OutboxEventStore outboxEventStore;
    private final DeliveryAddressService deliveryAddressService;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReviewRepository reviewRepository;

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

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            // 상품 판매 상태 검증 — INACTIVE/DISCONTINUED 상품은 주문 불가
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            // 단가 검증 — 요청값이 현재 상품 가격과 일치해야 한다
            if (product.getPrice().compareTo(itemRequest.getUnitPrice()) != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("상품 '%s'의 단가가 일치하지 않습니다. (요청: %s, 현재: %s)",
                                product.getName(), itemRequest.getUnitPrice(), product.getPrice()));
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(itemRequest.getUnitPrice())
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
    public OrderResponse getByIdForUser(Long id, String username, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwnership(order, username, isAdmin);
        return OrderResponse.from(order);
    }

    /**
     * 주문 + 결제 + 배송 정보를 단일 응답으로 반환한다 (프론트 상세 페이지용).
     * 소유권 검증은 {@link #getByIdForUser}와 동일하다.
     */
    public OrderDetailResponse getDetail(Long id, String username, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwnership(order, username, isAdmin);
        PaymentResponse payment = paymentRepository.findByOrderId(order.getId())
                .map(PaymentResponse::from)
                .orElse(null);
        ShipmentResponse shipment = shipmentRepository.findByOrderId(order.getId())
                .map(ShipmentResponse::from)
                .orElse(null);
        Set<Long> reviewedIds = reviewedProductIds(order.getUserId(), order.getItems());
        return OrderDetailResponse.builder()
                .order(OrderResponse.from(order, reviewedIds))
                .payment(payment)
                .shipment(shipment)
                .build();
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
    public Page<OrderResponse> getList(String username, boolean isAdmin,
                                       OrderSearchRequest request, Pageable pageable) {
        if (!isAdmin) {
            // USER: 본인 주문만 조회
            Long userId = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                    .getId();
            request.setUserId(userId);
        }
        return orderRepository.findAll(OrderSpecification.of(request), pageable)
                .map(OrderResponse::from);
    }

    /**
     * 특정 주문의 상태 변경 이력을 시간순으로 조회한다.
     *
     * <p>ADMIN은 모든 주문 이력에 접근 가능하고, USER는 본인 주문 이력만 조회할 수 있다.
     */
    public List<OrderStatusHistoryResponse> getHistory(Long orderId, String username, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwnership(order, username, isAdmin);
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
    public OrderResponse cancel(Long id, String username, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 요청자가 주문 소유자인지 검증 (ADMIN은 모든 주문 취소 가능)
        validateOrderOwnership(order, username, isAdmin);

        // 상태 검증 + CANCELLED 전환 (PENDING이 아니면 INVALID_ORDER_STATUS 예외)
        order.cancel();

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
     * Confirms the order after successful payment – called by the Payment domain.
     *
     * <p>Transitions PENDING → CONFIRMED and moves each item's inventory
     * from {@code reserved} to {@code allocated}.
     *
     * @param id order ID to confirm
     */
    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#id")
    public void confirm(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.confirm();

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmAllocation(item.getProduct().getId(), item.getQuantity());
        }

        recordHistory(order.getId(), OrderStatus.PENDING, OrderStatus.CONFIRMED, null);
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
        Order order = orderRepository.findByIdWithItems(id)
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

    // ===== 내부 헬퍼 =====

    /**
     * 주문 소유권을 검증한다.
     * ADMIN은 모든 주문에 접근 가능하고, USER는 본인 주문만 접근할 수 있다.
     *
     * @throws BusinessException USER가 타인 주문에 접근 시 {@code ORDER_ACCESS_DENIED}
     */
    private void validateOrderOwnership(Order order, String username, boolean isAdmin) {
        if (!isAdmin) {
            Long userId = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getId();
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
        }
    }

    /** 주문 항목들의 상품 ID 중 사용자가 리뷰를 작성한 ID 집합을 반환한다. */
    private Set<Long> reviewedProductIds(Long userId, List<OrderItem> items) {
        List<Long> productIds = items.stream()
                .map(i -> i.getProduct().getId())
                .collect(Collectors.toList());
        return new java.util.HashSet<>(reviewRepository.findReviewedProductIdsByUserId(userId, productIds));
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
