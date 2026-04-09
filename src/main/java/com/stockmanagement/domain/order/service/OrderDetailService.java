package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.dto.OrderDetailResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.dto.PaymentResponse;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 상세 통합 조회 서비스.
 *
 * <p>주문 + 결제 + 배송 정보를 단일 응답으로 조합한다.
 * {@link OrderService}에서 결제/배송/리뷰 도메인 의존성을 분리하기 위해 별도 서비스로 추출했다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderDetailService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 주문 + 결제 + 배송 정보를 단일 응답으로 반환한다 (프론트 상세 페이지용).
     *
     * <p>ADMIN은 모든 주문에 접근 가능하고, USER는 본인 주문만 조회할 수 있다.
     */
    public OrderDetailResponse getDetail(Long id, Long userId, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOwnership(order, userId, isAdmin);

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

    private void validateOwnership(Order order, Long userId, boolean isAdmin) {
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private Set<Long> reviewedProductIds(Long userId, List<OrderItem> items) {
        List<Long> productIds = items.stream()
                .map(i -> i.getProduct().getId())
                .collect(Collectors.toList());
        return new java.util.HashSet<>(reviewRepository.findReviewedProductIdsByUserId(userId, productIds));
    }
}
