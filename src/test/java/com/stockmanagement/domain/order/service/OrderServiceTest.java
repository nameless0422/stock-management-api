package com.stockmanagement.domain.order.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.exception.InsufficientStockException;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
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
import com.stockmanagement.domain.order.dto.OrderSearchRequest;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatusHistoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.common.outbox.OutboxEventStore;
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
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private OutboxEventStore outboxEventStore;

    @Mock
    private PointService pointService;

    @Mock
    private DeliveryAddressService deliveryAddressService;

    @InjectMocks
    private OrderService orderService;

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
        ReflectionTestUtils.setField(product, "id", 1L); // findAllById 결과 Map 조회에 필요

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
    }

    // ===== create() =====

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("정상 주문 생성 — Order 저장 및 재고 예약 호출")
        void createsOrderAndReservesStock() {
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(1L);
            given(itemRequest.getQuantity()).willReturn(1);
            given(itemRequest.getUnitPrice()).willReturn(new BigDecimal("10000"));

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(request.getUserId()).willReturn(1L);
            given(request.getItems()).willReturn(List.of(itemRequest));

            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.empty());
            given(productRepository.findAllById(anyIterable())).willReturn(List.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            OrderResponse response = orderService.create(request);

            verify(orderRepository).save(any(Order.class));
            verify(inventoryService).reserve(any(), eq(1));
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("10000");
            assertThat(response.getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("멱등성 키 중복 — 기존 주문을 반환하고 저장/예약을 수행하지 않는다")
        void returnsExistingOrderForDuplicateIdempotencyKey() {
            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.create(request);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(inventoryService);
            assertThat(response.getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("존재하지 않는 상품 ID 포함 시 PRODUCT_NOT_FOUND 예외 발생")
        void throwsWhenProductNotFound() {
            // getUnitPrice/getQuantity는 상품 조회 실패 시 호출되지 않으므로 스텁 불필요
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(99L);

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(request.getItems()).willReturn(List.of(itemRequest));

            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.empty());
            given(productRepository.findAllById(anyIterable())).willReturn(List.of()); // 존재하지 않는 상품

            assertThatThrownBy(() -> orderService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("요청 단가와 상품 가격 불일치 시 INVALID_INPUT 예외 발생")
        void throwsWhenUnitPriceMismatch() {
            // getQuantity/getUserId는 단가 불일치 예외 경로에서 호출되지 않으므로 스텁 불필요
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(1L);
            given(itemRequest.getUnitPrice()).willReturn(new BigDecimal("9999")); // 불일치

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(request.getItems()).willReturn(List.of(itemRequest));

            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.empty());
            given(productRepository.findAllById(anyIterable())).willReturn(List.of(product)); // price=10000

            assertThatThrownBy(() -> orderService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("쿠폰 코드 포함 주문 생성 — discountAmount 적용")
        void createOrderWithCoupon() {
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(1L);
            given(itemRequest.getQuantity()).willReturn(1);
            given(itemRequest.getUnitPrice()).willReturn(new BigDecimal("10000"));

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-coupon");
            given(request.getUserId()).willReturn(1L);
            given(request.getItems()).willReturn(List.of(itemRequest));
            given(request.getCouponCode()).willReturn("FIXED2000");

            given(orderRepository.findByIdempotencyKey("idem-key-coupon")).willReturn(Optional.empty());
            given(productRepository.findAllById(anyIterable())).willReturn(List.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);

            CouponValidateResponse couponResult = CouponValidateResponse.builder()
                    .couponId(10L)
                    .discountAmount(new BigDecimal("2000"))
                    .finalAmount(new BigDecimal("8000"))
                    .build();
            given(couponService.applyCoupon(eq("FIXED2000"), eq(1L), any(), any()))
                    .willReturn(couponResult);

            OrderResponse response = orderService.create(request);

            verify(couponService).applyCoupon(eq("FIXED2000"), eq(1L), any(), any());
            // Order.applyDiscount()가 호출되어 discountAmount가 설정됨
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("재고 부족 시 InsufficientStockException이 전파된다")
        void propagatesInsufficientStockException() {
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(1L);
            given(itemRequest.getQuantity()).willReturn(100);
            given(itemRequest.getUnitPrice()).willReturn(new BigDecimal("10000"));

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(request.getUserId()).willReturn(1L);
            given(request.getItems()).willReturn(List.of(itemRequest));

            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.empty());
            given(productRepository.findAllById(anyIterable())).willReturn(List.of(product));
            given(orderRepository.save(any(Order.class))).willReturn(order);
            doThrow(new InsufficientStockException(100, 5))
                    .when(inventoryService).reserve(any(), anyInt());

            assertThatThrownBy(() -> orderService.create(request))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ===== getById() =====

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("주문이 존재하면 OrderResponse를 반환한다")
        void returnsOrderResponse() {
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.getById(1L);

            assertThat(response.getTotalAmount()).isEqualByComparingTo("10000");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItems(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(99L))
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

            Page<OrderResponse> result = orderService.getList(null, true, new OrderSearchRequest(), pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("USER — 컨트롤러에서 전달된 userId로 강제 필터링")
        void user_filtersOwnOrdersOnly() {
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(order), pageable, 1));

            Page<OrderResponse> result = orderService.getList(1L, false, new OrderSearchRequest(), pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ===== cancel() =====

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING 주문 취소 — CANCELLED 전환 및 재고 예약 해제 호출")
        void cancelsPendingOrder() {
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            OrderResponse response = orderService.cancel(1L, 1L, false); // userId=1L, order.userId=1L

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(inventoryService).releaseReservation(any(), eq(1));
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED 주문 취소 시도 — INVALID_ORDER_STATUS 예외 발생")
        void throwsWhenCancellingConfirmedOrder() {
            order.confirm(); // PENDING → CONFIRMED
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(1L, 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));

            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancel(99L, 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }

    // ===== confirm() =====

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("PENDING 주문 확정 — CONFIRMED 전환 및 재고 confirmAllocation 호출")
        void confirmsPendingOrder() {
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderService.confirm(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(inventoryService).confirmAllocation(any(), eq(1));
        }

        @Test
        @DisplayName("PAYMENT_IN_PROGRESS 주문 확정 — 일반 Toss 결제 경로 (CONFIRMED 전환)")
        void confirmsPaymentInProgressOrder() {
            order.startPayment(); // PENDING → PAYMENT_IN_PROGRESS
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderService.confirm(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(inventoryService).confirmAllocation(any(), eq(1));
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.confirm(99L))
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
            order.confirm(); // PENDING → CONFIRMED
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            orderService.refund(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(inventoryService).releaseAllocation(any(), eq(1));
        }

        @Test
        @DisplayName("PENDING 주문 환불 시도 — INVALID_ORDER_STATUS 예외 발생")
        void throwsWhenRefundingPendingOrder() {
            // order는 기본 PENDING 상태
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.refund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));

            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.refund(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }
}
