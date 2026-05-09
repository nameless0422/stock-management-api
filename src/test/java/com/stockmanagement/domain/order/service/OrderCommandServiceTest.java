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

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandService 단위 테스트")
class OrderCommandServiceTest {

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
    private OrderCommandService orderCommandService;

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

            OrderResponse response = orderCommandService.create(request);

            verify(orderRepository).save(any(Order.class));
            verify(inventoryService).reserve(any(), eq(1));
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("10000");
            assertThat(response.getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("멱등성 키 경쟁 조건 — save()에서 DataIntegrityViolationException 발생 시 기존 주문 반환")
        void returnsExistingOrderOnDataIntegrityViolation() {
            OrderItemRequest itemRequest = mock(OrderItemRequest.class);
            given(itemRequest.getProductId()).willReturn(1L);
            given(itemRequest.getQuantity()).willReturn(1);
            given(itemRequest.getUnitPrice()).willReturn(new BigDecimal("10000"));

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-race");
            given(request.getUserId()).willReturn(1L);
            given(request.getItems()).willReturn(List.of(itemRequest));

            given(orderRepository.findByIdempotencyKey("idem-key-race"))
                    .willReturn(Optional.empty())   // 1차 조회: 없음 → 저장 진행
                    .willReturn(Optional.of(order)); // 2차 조회(catch 내부): 기존 주문 반환
            given(productRepository.findAllById(anyIterable())).willReturn(List.of(product));
            given(orderRepository.save(any(Order.class)))
                    .willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

            OrderResponse response = orderCommandService.create(request);

            assertThat(response.getIdempotencyKey()).isEqualTo("idem-key-001");
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("멱등성 키 중복 — 기존 주문을 반환하고 저장/예약을 수행하지 않는다")
        void returnsExistingOrderForDuplicateIdempotencyKey() {
            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-001");
            given(orderRepository.findByIdempotencyKey("idem-key-001")).willReturn(Optional.of(order));

            OrderResponse response = orderCommandService.create(request);

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

            assertThatThrownBy(() -> orderCommandService.create(request))
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

            assertThatThrownBy(() -> orderCommandService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 상품 중복 포함 시 INVALID_INPUT 예외 발생")
        void throwsWhenDuplicateProductId() {
            OrderItemRequest item1 = mock(OrderItemRequest.class);
            given(item1.getProductId()).willReturn(1L);
            OrderItemRequest item2 = mock(OrderItemRequest.class);
            given(item2.getProductId()).willReturn(1L); // 중복 상품

            OrderCreateRequest request = mock(OrderCreateRequest.class);
            given(request.getIdempotencyKey()).willReturn("idem-key-dup");
            given(request.getItems()).willReturn(List.of(item1, item2));

            given(orderRepository.findByIdempotencyKey("idem-key-dup")).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderCommandService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(inventoryService);
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

            OrderResponse response = orderCommandService.create(request);

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

            assertThatThrownBy(() -> orderCommandService.create(request))
                    .isInstanceOf(InsufficientStockException.class);
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

            OrderResponse response = orderCommandService.cancel(1L, 1L, false, null); // userId=1L, order.userId=1L

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(inventoryService).releaseReservation(any(), eq(1));
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED 주문 취소 시도 — INVALID_ORDER_STATUS 예외 발생")
        void throwsWhenCancellingConfirmedOrder() {
            order.confirm(); // PENDING → CONFIRMED
            given(orderRepository.findByIdWithItemsForUpdate(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCommandService.cancel(1L, 1L, false, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));

            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(orderRepository.findByIdWithItemsForUpdate(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderCommandService.cancel(99L, 1L, false, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        }
    }
}
