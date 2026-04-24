package com.stockmanagement.domain.payment.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment 엔티티 단위 테스트")
class PaymentTest {

    private Payment donePayment;

    @BeforeEach
    void setUp() {
        donePayment = Payment.builder()
                .orderId(1L)
                .tossOrderId("toss-001")
                .amount(new BigDecimal("10000"))
                .build();
        donePayment.approve("pk-001", "카드", LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("cancel()")
    class CancelTest {

        @Test
        @DisplayName("전액 취소 — cancelAmount null이면 CANCELLED, cancelledAmount = amount")
        void fullCancel() {
            donePayment.cancel("고객 요청", null);

            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(donePayment.getCancelledAmount()).isEqualByComparingTo("10000");
            assertThat(donePayment.getCancelReason()).isEqualTo("고객 요청");
        }

        @Test
        @DisplayName("부분 취소 — cancelAmount < amount이면 PARTIAL_CANCELLED")
        void partialCancel() {
            donePayment.cancel("부분 환불", new BigDecimal("3000"));

            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELLED);
            assertThat(donePayment.getCancelledAmount()).isEqualByComparingTo("3000");
        }

        @Test
        @DisplayName("부분 취소 누적 — 누적 금액이 amount 이상이면 CANCELLED로 전환")
        void accumulatedPartialCancelBecomesFullCancel() {
            donePayment.cancel("1차 부분 환불", new BigDecimal("6000"));
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELLED);

            donePayment.cancel("2차 부분 환불", new BigDecimal("4000"));
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(donePayment.getCancelledAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("PARTIAL_CANCELLED 상태에서 추가 부분 취소 가능")
        void partialCancelFromPartialCancelled() {
            donePayment.cancel("1차", new BigDecimal("2000"));
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELLED);

            donePayment.cancel("2차", new BigDecimal("3000"));
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELLED);
            assertThat(donePayment.getCancelledAmount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("PARTIAL_CANCELLED 상태에서 전액 취소(null) 가능")
        void fullCancelFromPartialCancelled() {
            donePayment.cancel("1차 부분", new BigDecimal("3000"));

            donePayment.cancel("나머지 전액", null);
            assertThat(donePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(donePayment.getCancelledAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("PENDING 상태에서 취소하면 INVALID_PAYMENT_STATUS 예외")
        void throwsWhenPending() {
            Payment pending = Payment.builder()
                    .orderId(1L).tossOrderId("t-1").amount(new BigDecimal("5000")).build();

            assertThatThrownBy(() -> pending.cancel("취소", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));
        }

        @Test
        @DisplayName("CANCELLED 상태에서 재취소하면 INVALID_PAYMENT_STATUS 예외")
        void throwsWhenAlreadyCancelled() {
            donePayment.cancel("취소", null);

            assertThatThrownBy(() -> donePayment.cancel("재취소", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS));
        }
    }
}
