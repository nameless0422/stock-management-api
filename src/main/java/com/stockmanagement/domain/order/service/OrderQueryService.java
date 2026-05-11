package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewRequest;
import com.stockmanagement.domain.order.dto.OrderPreviewResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.dto.OrderStatusHistoryResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderSpecification;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 주문 조회 전용 서비스.
 *
 * <p>읽기 전용 트랜잭션으로 주문 단건/목록/이력/미리보기 조회를 담당한다.
 * DB 쓰기 연산은 수행하지 않으며, 캐시 히트를 통해 반복 조회 성능을 높인다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CouponService couponService;
    private final PointService pointService;

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
     *   <li>USER: userId를 강제 적용 (request.userId 무시)
     * </ul>
     */
    public Page<OrderResponse> getList(Long userId, boolean isAdmin,
                                       OrderSearchRequest request, Pageable pageable) {
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
     * <p>오프셋 방식과 달리 COUNT 쿼리가 없어 대용량 이력 스크롤에 적합하다.
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
        Long orderUserId = orderRepository.findUserIdById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!isAdmin && !orderUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream().map(OrderStatusHistoryResponse::from).toList();
    }

    /**
     * 주문 금액을 미리보기 계산한다 (DB 쓰기 없음).
     *
     * <p>쿠폰·포인트를 적용한 최종 결제 금액과 적립 예정 포인트를 반환한다.
     */
    public OrderPreviewResponse preview(OrderPreviewRequest request, Long userId) {
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList();
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "동일 상품이 중복으로 포함되어 있습니다. 수량을 합쳐서 제출해주세요.");
        }

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
            var validateReq = new CouponValidateRequest(
                    request.getCouponCode(), originalAmount);
            couponDiscount = couponService.validate(userId, validateReq).getDiscountAmount();
        }

        // 포인트 잔액 + 한도 검증 (차감은 하지 않음)
        long usePoints = request.getUsePoints();
        if (usePoints > 0) {
            pointService.validateBalance(userId, usePoints);
            BigDecimal maxUsablePoints = originalAmount.subtract(couponDiscount);
            if (BigDecimal.valueOf(usePoints).compareTo(maxUsablePoints) > 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "포인트 사용 금액이 결제 가능 금액을 초과합니다. 최대 사용 가능 포인트: "
                                + maxUsablePoints.longValue() + "P");
            }
        }
        BigDecimal pointDiscount = BigDecimal.valueOf(usePoints);

        BigDecimal shippingFee = BigDecimal.ZERO;
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

    private void validateOrderOwnership(Order order, Long userId, boolean isAdmin) {
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }
}
