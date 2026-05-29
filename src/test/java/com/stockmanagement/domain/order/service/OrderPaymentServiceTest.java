package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentService 단위 테스트")
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CouponService couponService;

    @Mock
    private PointService pointService;

    @Mock
    private OutboxEventStore outboxEventStore;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    private Product product;
    private ProductVariant variant;
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

        variant = ProductVariant.builder()
                .product(product)
                .optionName("기본")
                .sku("SKU-001")
                .price(new BigDecimal("10000"))
                .build();
        ReflectionTestUtils.setField(variant, "id", 1L);

        order = Order.builder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000"))
                .idempotencyKey("idem-key-001")
                .build();

        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .variant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("10000"))
                .build();
        order.addItem(orderItem);
    }

    // ===== confirm() =====

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("PENDING 주문 확정 — CONFIRMED 전환 및 재고 confirmAllocation 호출")
        void confirmsPendingOrder() {
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderPaymentService.confirm(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(inventoryService).confirmAllocation(eq(1L), eq(1));
        }

        @Test
        @DisplayName("PAYMENT_IN_PROGRESS 주문 확정 — 일반 Toss 결제 경로 (CONFIRMED 전환)")
        void confirmsPaymentInProgressOrder() {
            order.startPayment(); // PENDING -> PAYMENT_IN_PROGRESS
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderPaymentService.confirm(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(inventoryService).confirmAllocation(eq(1L), eq(1));
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderPaymentService.confirm(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }

    // ===== refund() =====

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("CONFIRMED 주문 환불 — CANCELLED 전환 및 재고 releaseAllocation 호출")
        void refundsConfirmedOrder() {
            order.confirm(); // PENDING -> CONFIRMED
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderPaymentService.refund(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(inventoryService).releaseAllocation(eq(1L), eq(1));
        }

        @Test
        @DisplayName("PENDING 주문 환불 시도 — INVALID_ORDER_STATUS 예외 발생")
        void throwsWhenRefundingPendingOrder() {
            // order는 기본 PENDING 상태
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPaymentService.refund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));

            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderPaymentService.refund(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }
}
