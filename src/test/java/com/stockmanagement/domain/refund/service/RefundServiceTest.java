package com.stockmanagement.domain.refund.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.entity.RefundStatus;
import com.stockmanagement.domain.refund.repository.RefundRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
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
    @Mock private UserRepository userRepository;

    @InjectMocks private RefundService refundService;

    private User mockUser(Long id) {
        User u = User.builder().username("user1").password("pw").email("e@e.com").build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

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
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            Payment payment = mockPayment(10L, 100L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(refundRepository.existsByPaymentId(10L)).willReturn(false);

            Refund saved = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("단순 변심")
                    .build();
            ReflectionTestUtils.setField(saved, "id", 5L);
            given(refundRepository.save(any())).willReturn(saved);

            RefundResponse response = refundService.requestRefund(mockRequest(10L), "user1");

            assertThat(response.getPaymentId()).isEqualTo(10L);
            verify(paymentService).cancel(eq("pk_test_abc123"), any());
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        }

        @Test
        @DisplayName("중복 환불 요청 → REFUND_ALREADY_EXISTS")
        void duplicate() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            given(paymentRepository.findById(10L)).willReturn(Optional.of(mockPayment(10L, 100L)));
            given(refundRepository.existsByPaymentId(10L)).willReturn(true);

            assertThatThrownBy(() -> refundService.requestRefund(mockRequest(10L), "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("PaymentService.cancel() 실패 → FAILED 상태, 예외 전파")
        void failsWhenPaymentCancelThrows() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            Payment payment = mockPayment(10L, 100L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(refundRepository.existsByPaymentId(10L)).willReturn(false);

            Refund saved = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("단순 변심")
                    .build();
            given(refundRepository.save(any())).willReturn(saved);
            doThrow(new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR))
                    .when(paymentService).cancel(any(), any());

            assertThatThrownBy(() -> refundService.requestRefund(mockRequest(10L), "user1"))
                    .isInstanceOf(BusinessException.class);
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("getByPaymentId() — 결제 ID 조회")
    class GetByPaymentId {

        @Test
        @DisplayName("존재하면 반환")
        void found() {
            User user = mockUser(1L);
            Order order = Order.builder().userId(1L).idempotencyKey("k").build();
            ReflectionTestUtils.setField(order, "id", 100L);
            Refund refund = Refund.builder()
                    .paymentId(10L).orderId(100L)
                    .amount(BigDecimal.valueOf(50000)).reason("변심")
                    .build();
            ReflectionTestUtils.setField(refund, "id", 5L);
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
            given(refundRepository.findByPaymentId(10L)).willReturn(Optional.of(refund));
            given(orderRepository.findById(100L)).willReturn(Optional.of(order));

            RefundResponse response = refundService.getByPaymentId(10L, "user1");

            assertThat(response.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("없으면 REFUND_NOT_FOUND")
        void notFound() {
            User user = mockUser(1L);
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
            given(refundRepository.findByPaymentId(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.getByPaymentId(10L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
        }
    }
}
