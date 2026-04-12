package com.stockmanagement.domain.payment.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.infrastructure.PaymentIdempotencyManager;
import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private PaymentIdempotencyManager idempotencyManager;

    @Mock
    private PaymentTransactionHelper transactionHelper;

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
    }

    // ===== prepare() =====

    @Nested
    @DisplayName("prepare()")
    class Prepare {

        @Test
        @DisplayName("정상 준비 — Payment 저장 후 tossOrderId와 금액 반환")
        void savesPaymentAndReturnsPrepareResponse() {
            PaymentPrepareRequest request = mock(PaymentPrepareRequest.class);
            given(request.getOrderId()).willReturn(1L);
            given(request.getAmount()).willReturn(new BigDecimal("10000"));

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));
            given(paymentRepository.findByOrderId(pendingOrder.getId())).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willReturn(pendingPayment);

            PaymentPrepareResponse response = paymentService.prepare(request, 1L);

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

            assertThatThrownBy(() -> paymentService.prepare(request, 1L))
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
            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.prepare(request, 1L))
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

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.prepare(request, 1L))
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

            given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(pendingOrder));
            given(paymentRepository.findByOrderId(pendingOrder.getId()))
                    .willReturn(Optional.of(pendingPayment)); // 기존 PENDING Payment

            PaymentPrepareResponse response = paymentService.prepare(request, 1L);

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
            successResponse = mock(TossConfirmResponse.class);
            lenient().when(successResponse.getStatus()).thenReturn("DONE");
            lenient().when(successResponse.getPaymentKey()).thenReturn("pk-001");
            lenient().when(successResponse.getMethod()).thenReturn("카드");
            lenient().when(successResponse.getRequestedAt()).thenReturn("2024-01-01T00:00:00+09:00");
            lenient().when(successResponse.getApprovedAt()).thenReturn("2024-01-01T00:00:01+09:00");
        }

        @Test
        @DisplayName("정상 승인 — 검증 TX, Toss API 호출, 확정 TX 순서 보장")
        void confirmsPendingPayment() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));
            given(request.getPaymentKey()).willReturn("pk-001");

            given(transactionHelper.loadAndValidateForConfirm(anyString(), any(), anyLong()))
                    .willReturn(Optional.empty());
            given(tossPaymentsClient.confirm(any())).willReturn(successResponse);

            pendingPayment.approve("pk-001", "카드", LocalDateTime.now(), LocalDateTime.now());
            PaymentResponse doneResponse = PaymentResponse.from(pendingPayment);
            given(transactionHelper.applyConfirmResult(eq("toss-order-001"), any())).willReturn(doneResponse);

            PaymentResponse response = paymentService.confirm(request, 1L);

            verify(tossPaymentsClient).confirm(any());
            verify(transactionHelper).applyConfirmResult(eq("toss-order-001"), any());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("이미 DONE 상태이면 Toss API 호출 없이 현재 상태를 반환한다 (멱등성)")
        void returnsExistingDonePaymentWithoutApiCall() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));

            pendingPayment.approve("pk-001", "카드", LocalDateTime.now(), LocalDateTime.now());
            PaymentResponse doneResponse = PaymentResponse.from(pendingPayment);
            given(transactionHelper.loadAndValidateForConfirm(anyString(), any(), anyLong()))
                    .willReturn(Optional.of(doneResponse));

            PaymentResponse response = paymentService.confirm(request, 1L);

            verifyNoInteractions(tossPaymentsClient);
            verify(transactionHelper, never()).applyConfirmResult(any(), any());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.DONE);
        }

        @Test
        @DisplayName("PENDING이 아닌 상태(FAILED)이면 PAYMENT_ALREADY_PROCESSED 예외 발생")
        void throwsForNonPendingPayment() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));

            given(transactionHelper.loadAndValidateForConfirm(anyString(), any(), anyLong()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED));

            assertThatThrownBy(() -> paymentService.confirm(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED));

            verifyNoInteractions(tossPaymentsClient);
        }

        @Test
        @DisplayName("요청 금액이 DB 금액과 불일치하면 PAYMENT_AMOUNT_MISMATCH 예외 발생")
        void throwsWhenAmountMismatch() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("9999")); // 불일치

            given(transactionHelper.loadAndValidateForConfirm(anyString(), any(), anyLong()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

            assertThatThrownBy(() -> paymentService.confirm(request, 1L))
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

            PaymentResponse response = paymentService.confirm(request, 1L);

            assertThat(response).isSameAs(cached);
            verifyNoInteractions(transactionHelper, tossPaymentsClient);
        }

        @Test
        @DisplayName("PROCESSING 선점 실패 시 PAYMENT_PROCESSING_IN_PROGRESS 예외 발생")
        void throwsWhenProcessingInProgress() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");

            given(idempotencyManager.tryAcquire("confirm:toss-order-001")).willReturn(false);

            assertThatThrownBy(() -> paymentService.confirm(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS));

            verifyNoInteractions(transactionHelper, tossPaymentsClient);
        }

        @Test
        @DisplayName("Toss API가 DONE이 아닌 상태 반환 시 applyConfirmResult가 TOSS_PAYMENTS_ERROR를 던진다")
        void failsWhenTossReturnsNonDoneStatus() {
            PaymentConfirmRequest request = mock(PaymentConfirmRequest.class);
            given(request.getTossOrderId()).willReturn("toss-order-001");
            given(request.getAmount()).willReturn(new BigDecimal("10000"));
            given(request.getPaymentKey()).willReturn("pk-001");

            TossConfirmResponse failResponse = mock(TossConfirmResponse.class);

            given(transactionHelper.loadAndValidateForConfirm(anyString(), any(), anyLong()))
                    .willReturn(Optional.empty());
            given(tossPaymentsClient.confirm(any())).willReturn(failResponse);
            given(transactionHelper.applyConfirmResult(eq("toss-order-001"), any()))
                    .willThrow(new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR));

            assertThatThrownBy(() -> paymentService.confirm(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.TOSS_PAYMENTS_ERROR));
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
        @DisplayName("정상 취소 — 검증 TX, Toss 취소 API 호출, 취소 반영 TX 순서 보장")
        void cancelsDonePayment() {
            PaymentCancelRequest request = mock(PaymentCancelRequest.class);
            given(request.getCancelReason()).willReturn("고객 요청");
            given(request.getCancelAmount()).willReturn(null);

            given(transactionHelper.loadAndValidateForCancel(eq("pk-001"), anyLong(), anyBoolean())).willReturn(Optional.empty());

            donePayment.cancel("고객 요청");
            PaymentResponse cancelledResponse = PaymentResponse.from(donePayment);
            given(transactionHelper.applyCancelResult(eq("pk-001"), eq("고객 요청"))).willReturn(cancelledResponse);

            PaymentResponse response = paymentService.cancel("pk-001", request, 1L, false);

            verify(tossPaymentsClient).cancel(eq("pk-001"), any());
            verify(transactionHelper).applyCancelResult(eq("pk-001"), eq("고객 요청"));
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 CANCELLED이면 Toss API 호출 없이 현재 상태를 반환한다 (멱등성)")
        void returnsExistingCancelledPaymentWithoutApiCall() {
            donePayment.cancel("이미 취소됨");
            PaymentResponse cancelledResponse = PaymentResponse.from(donePayment);
            given(transactionHelper.loadAndValidateForCancel(eq("pk-001"), anyLong(), anyBoolean()))
                    .willReturn(Optional.of(cancelledResponse));

            PaymentResponse response = paymentService.cancel("pk-001", mock(PaymentCancelRequest.class), 1L, false);

            verifyNoInteractions(tossPaymentsClient);
            verify(transactionHelper, never()).applyCancelResult(any(), any());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("Redis에 완료 캐시가 있으면 DB/Toss API 호출 없이 캐시 결과를 반환한다 (Redis 멱등성)")
        void returnsCachedResponseWhenRedisHit() {
            PaymentResponse cached = mock(PaymentResponse.class);
            given(idempotencyManager.getIfCompleted("cancel:pk-001"))
                    .willReturn(Optional.of(cached));

            PaymentResponse response = paymentService.cancel("pk-001", mock(PaymentCancelRequest.class), 1L, false);

            assertThat(response).isSameAs(cached);
            verifyNoInteractions(transactionHelper, tossPaymentsClient);
        }

        @Test
        @DisplayName("PROCESSING 선점 실패 시 PAYMENT_PROCESSING_IN_PROGRESS 예외 발생")
        void throwsWhenProcessingInProgress() {
            given(idempotencyManager.tryAcquire("cancel:pk-001")).willReturn(false);

            assertThatThrownBy(() -> paymentService.cancel("pk-001", mock(PaymentCancelRequest.class), 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_PROCESSING_IN_PROGRESS));

            verifyNoInteractions(transactionHelper, tossPaymentsClient);
        }

        @Test
        @DisplayName("DONE이 아닌 상태(PENDING)는 INVALID_PAYMENT_STATUS 예외 발생")
        void throwsWhenPaymentNotDone() {
            given(transactionHelper.loadAndValidateForCancel(eq("pk-x"), anyLong(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS));

            assertThatThrownBy(() -> paymentService.cancel("pk-x", mock(PaymentCancelRequest.class), 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));

            verifyNoInteractions(tossPaymentsClient);
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 PAYMENT_NOT_FOUND 예외 발생")
        void throwsWhenPaymentNotFound() {
            given(transactionHelper.loadAndValidateForCancel(eq("unknown-pk"), anyLong(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            assertThatThrownBy(() -> paymentService.cancel("unknown-pk", mock(PaymentCancelRequest.class), 1L, false))
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
            given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(pendingPayment));

            Optional<PaymentResponse> result = paymentService.getByOrderId(10L, 1L, false);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("타인 주문 접근 시 ORDER_ACCESS_DENIED 예외 발생")
        void throwsForNonOwner() {
            given(orderRepository.findById(10L)).willReturn(Optional.of(confirmedOrder(10L, 99L)));

            assertThatThrownBy(() -> paymentService.getByOrderId(10L, 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
        }

        @Test
        @DisplayName("ADMIN은 소유권 검증 없이 조회 가능")
        void adminCanAccessAnyOrder() {
            given(paymentRepository.findByOrderId(10L)).willReturn(Optional.of(pendingPayment));

            Optional<PaymentResponse> result = paymentService.getByOrderId(10L, null, true);

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

            assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PAYMENT_STATUS_CHANGED + DONE + PENDING → 가상계좌 입금 확정 처리")
        void confirmsVirtualAccountDepositForPendingPayment() {
            TossWebhookEvent.Data data = mock(TossWebhookEvent.Data.class);
            given(data.getOrderId()).willReturn("toss-order-001");
            given(data.getStatus()).willReturn("DONE");
            given(data.getPaymentKey()).willReturn("pk-webhook-001");

            TossWebhookEvent event = mock(TossWebhookEvent.class);
            given(event.getEventType()).willReturn("PAYMENT_STATUS_CHANGED");
            given(event.getData()).willReturn(data);

            given(paymentRepository.findByTossOrderId("toss-order-001"))
                    .willReturn(Optional.of(pendingPayment));
            given(transactionHelper.applyWebhookConfirmResult("toss-order-001", "pk-webhook-001"))
                    .willReturn(mock(PaymentResponse.class));

            assertThatCode(() -> paymentService.handleWebhook(event)).doesNotThrowAnyException();
            verify(transactionHelper).applyWebhookConfirmResult("toss-order-001", "pk-webhook-001");
        }
    }
}
