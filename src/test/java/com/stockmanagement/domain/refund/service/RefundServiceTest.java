package com.stockmanagement.domain.refund.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import java.util.List;
import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.entity.RefundStatus;
import com.stockmanagement.domain.refund.repository.RefundRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundService 단위 테스트")
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks private RefundService refundService;

    private Payment mockPayment(Long id, Long orderId) {
        Payment p = Payment.builder()
                .orderId(orderId)
                .tossOrderId("toss-order-" + orderId)
                .amount(BigDecimal.valueOf(50000))
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "status", PaymentStatus.DONE);
        ReflectionTestUtils.setField(p, "paymentKey", "pk_test_abc123");
        return p;
    }

    private RefundRequest mockRequest(Long paymentId) {
        RefundRequest r = new RefundRequest();
        ReflectionTestUtils.setField(r, "paymentId", paymentId);
        ReflectionTestUtils.setField(r, "reason", "단순 변심");
        return r;
    }

    @Nested
    @DisplayName("requestRefund() — 환불 요청")
    class RequestRefund {

        @Test
        @DisplayName("정상 환불 → COMPLETED 상태")
        void success() {
            Payment payment = mockPayment(10L, 100L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(orderRepository.findUserIdById(100L)).willReturn(Optional.of(1L));
            given(refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());

            Refund saved = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("단순 변심")
                    .build();
            ReflectionTestUtils.setField(saved, "id", 5L);
            given(refundRepository.save(any())).willReturn(saved);

            RefundResponse response = refundService.requestRefund(mockRequest(10L), 1L);

            assertThat(response.getPaymentId()).isEqualTo(10L);
            verify(paymentService).cancel(eq("pk_test_abc123"), any(), eq(1L), eq(false));
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        }

        @Test
        @DisplayName("중복 환불 요청 (COMPLETED) → REFUND_ALREADY_EXISTS")
        void duplicate() {
            given(paymentRepository.findById(10L)).willReturn(Optional.of(mockPayment(10L, 100L)));
            given(orderRepository.findUserIdById(100L)).willReturn(Optional.of(1L));
            Refund completed = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("변심")
                    .build();
            completed.complete();
            given(refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(Optional.of(completed));

            assertThatThrownBy(() -> refundService.requestRefund(mockRequest(10L), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("이전 환불이 FAILED → 재시도 허용, COMPLETED 상태로 전이")
        void retryAfterFailed() {
            Payment payment = mockPayment(10L, 100L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(orderRepository.findUserIdById(100L)).willReturn(Optional.of(1L));

            Refund failedRefund = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("이전 사유")
                    .build();
            failedRefund.fail();
            ReflectionTestUtils.setField(failedRefund, "id", 5L);
            given(refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(Optional.of(failedRefund));

            refundService.requestRefund(mockRequest(10L), 1L);

            verify(paymentService).cancel(eq("pk_test_abc123"), any(), eq(1L), eq(false));
            assertThat(failedRefund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        }

        @Test
        @DisplayName("PaymentService.cancel() 실패 → FAILED 상태, 예외 전파")
        void failsWhenPaymentCancelThrows() {
            Payment payment = mockPayment(10L, 100L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(orderRepository.findUserIdById(100L)).willReturn(Optional.of(1L));
            given(refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());

            Refund saved = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("단순 변심")
                    .build();
            given(refundRepository.save(any())).willReturn(saved);
            doThrow(new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR))
                    .when(paymentService).cancel(any(), any(), any(), anyBoolean());

            assertThatThrownBy(() -> refundService.requestRefund(mockRequest(10L), 1L))
                    .isInstanceOf(BusinessException.class);
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("getByPaymentId() — 결제 ID 조회")
    class GetByPaymentId {

        @Test
        @DisplayName("존재하면 목록 반환")
        void found() {
            Refund refund = Refund.builder()
                    .paymentId(10L).orderId(100L).userId(1L)
                    .amount(BigDecimal.valueOf(50000)).reason("변심")
                    .build();
            ReflectionTestUtils.setField(refund, "id", 5L);
            given(refundRepository.findAllByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(List.of(refund));

            List<RefundResponse> responses = refundService.getByPaymentId(10L, 1L, false);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("없으면 REFUND_NOT_FOUND")
        void notFound() {
            given(refundRepository.findAllByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(List.of());

            assertThatThrownBy(() -> refundService.getByPaymentId(10L, 1L, false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
        }

        @Test
        @DisplayName("ADMIN은 타인 환불도 조회 가능")
        void adminCanAccessAnyRefund() {
            Refund refund = Refund.builder()
                    .paymentId(10L).orderId(100L).userId(1L)
                    .amount(BigDecimal.valueOf(50000)).reason("변심")
                    .build();
            ReflectionTestUtils.setField(refund, "id", 5L);
            given(refundRepository.findAllByPaymentIdOrderByCreatedAtDesc(10L)).willReturn(List.of(refund));

            List<RefundResponse> responses = refundService.getByPaymentId(10L, 99L, true);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(5L);
        }
    }
}
