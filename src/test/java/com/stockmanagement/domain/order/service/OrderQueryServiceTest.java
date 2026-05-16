package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderDeliverySnapshotRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService 단위 테스트")
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private OrderDeliverySnapshotRepository deliverySnapshotRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private PointService pointService;

    @InjectMocks
    private OrderQueryService orderQueryService;

    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("테스트 상품")
                .description("설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .build();
        ReflectionTestUtils.setField(product, "id", 1L);

        order = Order.builder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000"))
                .idempotencyKey("idem-key-001")
                .build();

        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .quantity(1)
                .unitPrice(new BigDecimal("10000"))
                .build();
        order.addItem(orderItem);

        lenient().when(shipmentRepository.findStatusMapByOrderIds(any())).thenReturn(new java.util.HashMap<>());
    }

    // ===== getById() =====

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("주문이 존재하면 OrderResponse를 반환한다")
        void returnsOrderResponse() {
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            OrderResponse response = orderQueryService.getById(1L);

            assertThat(response.getTotalAmount()).isEqualByComparingTo("10000");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItems(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderQueryService.getById(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }

    // ===== getList() =====

    @Nested
    @DisplayName("getList()")
    class GetList {

        @Test
        @DisplayName("ADMIN — 필터 없이 전체 주문 반환")
        void admin_returnsAllOrders() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order), pageable, 1));

            Page<OrderResponse> result = orderQueryService.getList(null, true, new OrderSearchRequest(), pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("USER — 컨트롤러에서 전달된 userId로 강제 필터링")
        void user_filtersOwnOrdersOnly() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order), pageable, 1));

            Page<OrderResponse> result = orderQueryService.getList(1L, false, new OrderSearchRequest(), pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
