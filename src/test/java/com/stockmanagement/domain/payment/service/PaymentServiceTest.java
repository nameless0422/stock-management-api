package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.infrastructure.PaymentIdempotencyManager;
import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.common.outbox.OutboxEventStore;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.domain.shipment.service.ShipmentService;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private PaymentIdempotencyManager idempotencyManager;

    @Mock
    private ShipmentService shipmentService;

    @Mock
    private OutboxEventStore outboxEventStore;

    @Mock
    private PointService pointService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentService paymentService;

    /** PENDING 상태 주문 픽스처 */
    private Order pendingOrder;

    /** PENDING 상태 결제 픽스처 */
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .userId(1L)
                .totalAmount(new BigDecimal("10000"))
                .idempotencyKey("idem-key-001")
                .build();

        pendingPayment = Payment.builder()
                .orderId(1L)
                .tossOrderId("toss-order-001")
                .amount(new BigDecimal("10000"))
                .build();

        // 기본: Redis에 캐시 없음, 선점 항상 성공
        lenient().when(idempotencyManager.getIfCompleted(anyString())).thenReturn(Optional.empty());
        lenient().when(idempotencyManager.tryAcquire(anyString())).thenReturn(true);

        // confirm() 소유권 검증용 기본 스텁 (pendingOrder.userId == 1L)
        User mockUser = User.builder().username("user").password("pw").email("e@test.com").build();
        ReflectionTestUtils.setField(mockUser, "id", 1L);
        lenient().when(userRepository.findByUsername("user")).thenReturn(Optional.of(mockUser));
        lenient().when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
    }

    // ===== prepare() =====

    @Nested
    @DisplayName("prepare()")
    class Prepare {

        private User mockUser() {
            User u = User.builder().username("user1").password("pw").email("e@e.com").build();
            ReflectionTestUtils.setField(u, "id", 1L); // pendingOrder.userId == 1L
            return u;
        }

        @Test
        @DisplayName("정상 준비 — Payment 저장 후 tossOrderId와 금액 반환")
        void savesPaymentAndReturnsPrepareResponse() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(1L);
            given(request.getAmount()).willReturn(new BigDecimal("10000"));

            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser()));
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));
            given(paymentRepository.findByOrderId(pendingOrder.getId())).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willReturn(pendingPayment);

            PaymentPrepareResponse response = paymentService.prepare(request, "user1");

            verify(paymentRepository).save(any(Payment.class));
            assertThat(response.getTossOrderId()).isEqualTo("toss-order-001");
            assertThat(response.getAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외 발생")
        void throwsWhenOrderNotFound() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(99L);
            given(orderRepository.findByIdWithItems(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.prepare(request, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_NOT_FOUND));

            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("PENDING이 아닌 주문은 INVALID_ORDER_STATUS 예외 발생")
        void throwsWhenOrderNotPending() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(1L);

            pendingOrder.confirm(); // PENDING → CONFIRMED
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser()));
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.prepare(request, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
        }

        @Test
        @DisplayName("요청 금액이 주문 총액과 불일치하면 PAYMENT_AMOUNT_MISMATCH 예외 발생")
        void throwsWhenAmountMismatch() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(1L);
            given(request.getAmount()).willReturn(new BigDecimal("9999")); // 불일치

            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser()));
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.prepare(request, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
        }

        @Test
        @DisplayName("기존 PENDING Payment가 있으면 저장 없이 기존 결제를 반환한다 (멱등성)")
        void returnsExistingPendingPaymentWithoutSaving() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(1L);
            given(request.getAmount()).willReturn(new BigDecimal("10000"));

            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser()));
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));
            given(paymentRepository.findByOrderId(pendingOrder.getId()))
                    .willReturn(Optional.of(pendingPayment)); // 기존 PENDING Payment

            PaymentPrepareResponse response = paymentService.prepare(request, "user1");

            verify(paymentRepository, never()).save(any());
            assertThat(response.getTossOrderId()).isEqualTo("toss-order-001");
        }
    }

    // ===== confirm() =====

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        private TossConfirmResponse successResponse;

        @BeforeEach
        void setUp() {
            // lenient: 예외 경로(이미 DONE, FAILED, 금액 불일치) 테스트에서는 Toss API를 호출하지 않으므로
            // successResponse 스텁이 사용되지 않아 UnnecessaryStubbingException 방지
            successResponse = mock(TossConfirmResponse.class);
            lenient().when(successResponse.getStatus()).thenReturn("DONE");
            lenient().when(successResponse.getPaymentKey()).thenReturn("pk-001");
            lenient().when(successResponse.getMethod()).thenReturn("카드");
            lenient().when(successResponse.getRequestedAt()).thenReturn("2024-01-01T00:00:00+09:00");
            lenient().when(successResponse.getApprovedAt()).thenReturn("2024-01-01T00:00:01+09:00");
        }

        @Test
        @DisplayName("정상 승인 — Toss API 호출, DONE 전환, 주문 확정")
        void confirmsPendingPayment() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));
            given(request.getPaymentKey()).willReturn("pk-001");

            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));
            given(tossPaymentsClient.confirm(any())).willReturn(successResponse);
            given(orderRepository.findById(1L)).willReturn(Optional.of(pendingOrder));

            PaymentResponse response = paymentService.confirm(request, "user");

            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
            verify(orderService).confirm(1L);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("이미 DONE 상태이면 Toss API 호출 없이 현재 상태를 반환한다 (멱등성)")
        void returnsExistingDonePaymentWithoutApiCall() {
            // getTossOrderId()만 사용, getAmount()/getPaymentKey()는 호출되지 않음
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");

            pendingPayment.approve("pk-001", "카드", LocalDateTime.now(), LocalDateTime.now());
            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));

            PaymentResponse response = paymentService.confirm(request, "user");

            verifyNoInteractions(tossPaymentsClient);
            verifyNoInteractions(orderService);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태(FAILED)이면 PAYMENT_ALREADY_PROCESSED 예외 발생")
        void throwsForNonPendingPayment() {
            // getAmount()는 상태 검증 실패 시 호출되지 않음
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");

            pendingPayment.fail("CARD_DECLINED", "카드 거절");
            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.confirm(request, "user"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED));

            verifyNoInteractions(tossPaymentsClient);
        }

        @Test
        @DisplayName("요청 금액이 DB 금액과 불일치하면 PAYMENT_AMOUNT_MISMATCH 예외 발생")
        void throwsWhenAmountMismatch() {
            // getPaymentKey()는 금액 불일치 시 호출되지 않음
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("9999")); // 불일치

            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.confirm(request, "user"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

            verifyNoInteractions(tossPaymentsClient);
        }

        @Test
        @DisplayName("Redis에 완료 캐시가 있으면 DB/Toss API 호출 없이 캐시 결과를 반환한다 (Redis 멱등성)")
        void returnsCachedResponseWhenRedisHit() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");

            PaymentResponse cached = mock(PaymentResponse.class);
            given(idempotencyManager.getIfCompleted("confirm:toss-order-001"))
                    .willReturn(Optional.of(cached));

            PaymentResponse response = paymentService.confirm(request, "user");

            assertThat(response).isSameAs(cached);
            verifyNoInteractions(paymentRepository, tossPaymentsClient, orderService);
        }

        @Test
        @DisplayName("PROCESSING 선점 실패 시 PAYMENT_PROCESSING_IN_PROGRESS 예외 발생")
        void throwsWhenProcessingInProgress() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");

            given(idempotencyManager.tryAcquire("confirm:toss-order-001")).willReturn(false);

            assertThatThrownBy(() -> paymentService.confirm(request, "user"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS));

            verifyNoInteractions(paymentRepository, tossPaymentsClient, orderService);
        }

        @Test
        @DisplayName("Toss API가 DONE이 아닌 상태 반환 시 FAILED 전환 후 TOSS_PAYMENTS_ERROR 예외 발생")
        void failsWhenTossReturnsNonDoneStatus() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));
            given(request.getPaymentKey()).willReturn("pk-001");

            TossConfirmResponse failResponse = mock(TossConfirmResponse.class);
            given(failResponse.getStatus()).willReturn("ABORTED");
            // getFailure()는 null 반환 (Mockito 기본값) — failureCode/Message = null로 처리됨

            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));
            given(tossPaymentsClient.confirm(any())).willReturn(failResponse);

            assertThatThrownBy(() -> paymentService.confirm(request, "user"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.TOSS_PAYMENTS_ERROR));

            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verifyNoInteractions(orderService);
        }
    }

    // ===== cancel() =====

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        /** 결제 취소 테스트용 DONE 상태 Payment */
        private Payment donePayment;

        @BeforeEach
        void setUp() {
            donePayment = Payment.builder()
                    .orderId(1L)
                    .tossOrderId("toss-order-001")
                    .amount(new BigDecimal("10000"))
                    .build();
            donePayment.approve("pk-001", "카드", LocalDateTime.now(), LocalDateTime.now());
        }

        @Test
        @DisplayName("정상 취소 — Toss 취소 API 호출, CANCELLED 전환, 주문 환불")
        void cancelsDonePayment() {
            PaymentCancelRequest request = mock(PaymentCancelRequest.class);
            given(request.getCancelReason()).willReturn("고객 요청");
            given(request.getCancelAmount()).willReturn(null);

            given(paymentRepository.findByPaymentKey("pk-001")).willReturn(Optional.of(donePayment));

            PaymentResponse response = paymentService.cancel("pk-001", request);

            verify(tossPaymentsClient).cancel(eq("pk-001"), any());
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(orderService).refund(1L);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 CANCELLED이면 Toss API 호출 없이 현재 상태를 반환한다 (멱등성)")
        void returnsExistingCancelledPaymentWithoutApiCall() {
            donePayment.cancel("이미 취소됨");
            given(paymentRepository.findByPaymentKey("pk-001")).willReturn(Optional.of(donePayment));

            PaymentResponse response = paymentService.cancel("pk-001", mock(PaymentCancelRequest.class));

            verifyNoInteractions(tossPaymentsClient);
            verifyNoInteractions(orderService);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("Redis에 완료 캐시가 있으면 DB/Toss API 호출 없이 캐시 결과를 반환한다 (Redis 멱등성)")
        void returnsCachedResponseWhenRedisHit() {
            PaymentResponse cached = mock(PaymentResponse.class);
            given(idempotencyManager.getIfCompleted("cancel:pk-001"))
                    .willReturn(Optional.of(cached));

            PaymentResponse response = paymentService.cancel("pk-001", mock(PaymentCancelRequest.class));

            assertThat(response).isSameAs(cached);
            verifyNoInteractions(paymentRepository, tossPaymentsClient, orderService);
        }

        @Test
        @DisplayName("PROCESSING 선점 실패 시 PAYMENT_PROCESSING_IN_PROGRESS 예외 발생")
        void throwsWhenProcessingInProgress() {
            given(idempotencyManager.tryAcquire("cancel:pk-001")).willReturn(false);

            assertThatThrownBy(() -> paymentService.cancel("pk-001", mock(PaymentCancelRequest.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS));

            verifyNoInteractions(paymentRepository, tossPaymentsClient, orderService);
        }

        @Test
        @DisplayName("DONE이 아닌 상태(PENDING)는 INVALID_PAYMENT_STATUS 예외 발생")
        void throwsWhenPaymentNotDone() {
            given(paymentRepository.findByPaymentKey("pk-x")).willReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.cancel("pk-x", mock(PaymentCancelRequest.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));

            verifyNoInteractions(tossPaymentsClient);
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 PAYMENT_NOT_FOUND 예외 발생")
        void throwsWhenPaymentNotFound() {
            given(paymentRepository.findByPaymentKey("unknown-pk")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.cancel("unknown-pk", mock(PaymentCancelRequest.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
        }
    }

    // ===== getByPaymentKey() =====

    @Nested
    @DisplayName("getByPaymentKey()")
    class GetByPaymentKey {

        @Test
        @DisplayName("결제가 존재하면 PaymentResponse를 반환한다")
        void returnsPaymentResponse() {
            given(paymentRepository.findByPaymentKey("pk-001")).willReturn(Optional.of(pendingPayment));

            PaymentResponse response = paymentService.getByPaymentKey("pk-001");

            assertThat(response.getTossOrderId()).isEqualTo("toss-order-001");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 PAYMENT_NOT_FOUND 예외 발생")
        void throwsWhenNotFound() {
            given(paymentRepository.findByPaymentKey("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getByPaymentKey("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
        }
    }

    // ===== getByOrderId() =====

    @Nested
    @DisplayName("getByOrderId()")
    class GetByOrderId {

        @Test
        @DisplayName("본인 주문이면 결제 정보를 반환한다")
        void returnsPaymentForOwner() {
            given(orderRepository.findById(10L)).willReturn(Optional.of(confirmedOrder(10L, 1L)));
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(stubUser(1L)));
            given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(pendingPayment));

            Optional<PaymentResponse> result = paymentService.getByOrderId(10L, "user1", false);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("타인 주문 접근 시 ORDER_ACCESS_DENIED 예외 발생")
        void throwsForNonOwner() {
            given(orderRepository.findById(10L)).willReturn(Optional.of(confirmedOrder(10L, 99L)));
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(stubUser(1L)));

            assertThatThrownBy(() -> paymentService.getByOrderId(10L, "user1", false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
        }

        @Test
        @DisplayName("ADMIN은 소유권 검증 없이 조회 가능")
        void adminCanAccessAnyOrder() {
            given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(pendingPayment));

            Optional<PaymentResponse> result = paymentService.getByOrderId(10L, "admin", true);

            assertThat(result).isPresent();
        }

        private Order confirmedOrder(Long orderId, Long userId) {
            Order o = Order.builder()
                    .userId(userId)
                    .totalAmount(BigDecimal.valueOf(10_000))
                    .build();
            ReflectionTestUtils.setField(o, "id", orderId);
            ReflectionTestUtils.setField(o, "status", OrderStatus.CONFIRMED);
            return o;
        }

        private User stubUser(Long userId) {
            User u = User.builder().username("user1").password("pw").email("u@test.com").build();
            ReflectionTestUtils.setField(u, "id", userId);
            return u;
        }
    }

    // ===== handleWebhook() =====

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("미지원 이벤트 타입은 무시한다")
        void ignoresUnsupportedEventType() {
            TossWebhookEvent event = mock(TossWebhookEvent.class);
            given(event.getEventType()).willReturn("BILLING_KEY_ISSUED");

            assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("PAYMENT_STATUS_CHANGED 이벤트에서 tossOrderId를 찾지 못하면 무시한다")
        void ignoresWhenPaymentNotFound() {
            TossWebhookEvent.Data data = mock(TossWebhookEvent.Data.class);
            given(data.getOrderId()).willReturn("unknown-toss-order");

            TossWebhookEvent event = mock(TossWebhookEvent.class);
            given(event.getEventType()).willReturn("PAYMENT_STATUS_CHANGED");
            given(event.getData()).willReturn(data);

            given(paymentRepository.findByTossOrderId("unknown-toss-order"))
                    .willReturn(Optional.empty());

            // data.getStatus()는 payment가 없으면 ifPresent 내부가 실행되지 않으므로 미호출
            assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PAYMENT_STATUS_CHANGED + DONE 수신 시 PENDING payment는 로그만 기록한다")
        void logsDoneEventForPendingPayment() {
            TossWebhookEvent.Data data = mock(TossWebhookEvent.Data.class);
            given(data.getOrderId()).willReturn("toss-order-001");
            given(data.getStatus()).willReturn("DONE");

            TossWebhookEvent event = mock(TossWebhookEvent.class);
            given(event.getEventType()).willReturn("PAYMENT_STATUS_CHANGED");
            given(event.getData()).willReturn(data);

            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment)); // PENDING 상태

            assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
            verifyNoInteractions(orderService);
        }
    }
}
