package com.stockmanagement.domain.shipment.service;

import com.stockmanagement.common.event.ShipmentDeliveredEvent;
import com.stockmanagement.common.event.ShipmentShippedEvent;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import com.stockmanagement.domain.shipment.dto.ShipmentUpdateRequest;
import com.stockmanagement.domain.shipment.entity.Shipment;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 비즈니스 로직 서비스.
 *
 * <p>상태 전이:
 * <pre>
 *   결제 완료(CONFIRMED)  → {@link #createForOrder}    : PREPARING 생성 (PaymentService 호출)
 *   PREPARING            → {@link #startShipping}     : SHIPPED (운송장 등록)
 *   SHIPPED              → {@link #completeDelivery}  : DELIVERED
 *   PREPARING|SHIPPED    → {@link #processReturn}     : RETURNED
 * </pre>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OutboxEventStore outboxEventStore;

    /**
     * 결제 완료된 주문에 대해 배송을 생성한다.
     * PaymentService.confirm()에서 호출된다.
     *
     * <p>{@code REQUIRES_NEW}: 결제 확정 트랜잭션과 독립적으로 커밋/롤백된다.
     * 배송 생성 실패가 결제 확정 트랜잭션을 rollback-only로 오염시키지 않도록 하기 위함.
     *
     * @throws BusinessException 주문이 CONFIRMED 상태가 아닌 경우, 또는 이미 배송이 존재하는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentResponse createForOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.SHIPMENT_ALREADY_EXISTS);
        }

        Shipment shipment = Shipment.builder().orderId(orderId).build();
        return ShipmentResponse.from(shipmentRepository.save(shipment));
    }

    /**
     * 주문 ID로 배송 정보를 조회한다. ADMIN은 전체 조회 가능, USER는 본인 주문만 가능.
     *
     * @throws BusinessException 배송 정보가 없는 경우, 또는 소유자가 아닌 경우
     */
    public ShipmentResponse getByOrderId(Long orderId, String username, boolean isAdmin) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND));

        if (!isAdmin) {
            // userId 스칼라 프로젝션 — Order 전체 엔티티 로드 불필요
            Long orderUserId = orderRepository.findUserIdById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND));
            if (!orderUserId.equals(resolveUserId(username))) {
                // 타인 배송 존재 여부 노출 방지 — ACCESS_DENIED 대신 NOT_FOUND 반환
                throw new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND);
            }
        }

        return ShipmentResponse.from(shipment);
    }

    /**
     * 로그인한 사용자의 배송 목록을 최신순 페이징 조회한다.
     *
     * @param userId   요청자 ID (JWT claim 기반)
     * @param pageable 페이지 요청
     */
    public Page<ShipmentResponse> getMyShipments(Long userId, Pageable pageable) {
        return shipmentRepository.findByUserId(userId, pageable)
                .map(ShipmentResponse::from);
    }

    /**
     * 배송을 출고 처리한다. (PREPARING → SHIPPED)
     *
     * @param orderId 주문 ID
     * @param request 택배사 + 운송장 번호
     */
    @Transactional
    public ShipmentResponse startShipping(Long orderId, ShipmentUpdateRequest request) {
        Shipment shipment = findByOrderIdOrThrow(orderId);
        shipment.ship(request.getCarrier(), request.getTrackingNumber());
        outboxEventStore.save(new ShipmentShippedEvent(orderId, request.getCarrier(), request.getTrackingNumber()));
        return ShipmentResponse.from(shipment);
    }

    /**
     * 배송 완료 처리한다. (SHIPPED → DELIVERED)
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public ShipmentResponse completeDelivery(Long orderId) {
        Shipment shipment = findByOrderIdOrThrow(orderId);
        shipment.deliver();
        outboxEventStore.save(new ShipmentDeliveredEvent(orderId));
        return ShipmentResponse.from(shipment);
    }

    /**
     * 반품 처리한다. (PREPARING|SHIPPED → RETURNED)
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public ShipmentResponse processReturn(Long orderId) {
        Shipment shipment = findByOrderIdOrThrow(orderId);
        shipment.processReturn();
        return ShipmentResponse.from(shipment);
    }

    /** JWT details에서 userId를 꺼낸다. details가 없으면 DB fallback (구 토큰·테스트 환경 호환). */
    private Long resolveUserId(String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }

    private Shipment findByOrderIdOrThrow(Long orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND));
    }
}
