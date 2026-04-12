package com.stockmanagement.integration;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.repository.RefundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Refund 통합 테스트.
 *
 * <p>환불 요청(POST /api/refunds)은 내부적으로 TossPayments API를 호출하므로
 * E2E 테스트가 불가하다. 대신 Payment/Refund 엔티티를 직접 저장하여
 * 조회 API와 중복 요청 방어 로직을 검증한다.
 */
@DisplayName("Refund 통합 테스트")
class RefundIntegrationTest extends AbstractIntegrationTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private OrderRepository orderRepository;

    // ===== 공통 헬퍼 =====

    /** CONFIRMED 주문을 DB에 직접 저장하고 ID를 반환한다. */
    private long createConfirmedOrder(long userId) {
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(BigDecimal.valueOf(20000))
                .idempotencyKey("order-" + System.nanoTime())
                .build();
        order.confirm();
        return orderRepository.save(order).getId();
    }

    /** DONE 상태의 Payment를 DB에 직접 저장하고 반환한다. */
    private Payment createDonePayment(long orderId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .tossOrderId("toss-" + System.nanoTime())
                .amount(BigDecimal.valueOf(20000))
                .build();
        payment.approve("pk-" + System.nanoTime(), "카드",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    /** COMPLETED 환불 레코드를 DB에 직접 저장하고 반환한다. */
    private Refund createCompletedRefund(long paymentId, long orderId) {
        Refund refund = Refund.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(20000))
                .reason("단순 변심")
                .build();
        refund.complete();
        return refundRepository.save(refund);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("환불 ID로 조회 → 200, 상태·금액 확인")
    void getById_success() throws Exception {
        String userToken = signupAndLogin("user1", "password1", "u1@test.com");
        long userId = userRepository.findByUsername("user1").orElseThrow().getId();

        long orderId = createConfirmedOrder(userId);
        Payment payment = createDonePayment(orderId);
        Refund refund = createCompletedRefund(payment.getId(), orderId);

        mockMvc.perform(get("/api/refunds/" + refund.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(refund.getId()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value(20000));
    }

    @Test
    @DisplayName("없는 환불 ID 조회 → 404")
    void getById_notFound() throws Exception {
        String userToken = signupAndLogin("user2", "password1", "u2@test.com");

        mockMvc.perform(get("/api/refunds/99999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("결제 ID로 환불 조회 → 200")
    void getByPaymentId_success() throws Exception {
        String userToken = signupAndLogin("user3", "password1", "u3@test.com");
        long userId = userRepository.findByUsername("user3").orElseThrow().getId();

        long orderId = createConfirmedOrder(userId);
        Payment payment = createDonePayment(orderId);
        createCompletedRefund(payment.getId(), orderId);

        mockMvc.perform(get("/api/refunds/payments/" + payment.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("이미 환불된 결제에 재요청 → 409 REFUND_ALREADY_EXISTS")
    void requestRefund_duplicate() throws Exception {
        String userToken = signupAndLogin("user4", "password1", "u4@test.com");
        long userId = userRepository.findByUsername("user4").orElseThrow().getId();

        long orderId = createConfirmedOrder(userId);
        Payment payment = createDonePayment(orderId);
        // 이미 환불 레코드가 존재하는 상태
        createCompletedRefund(payment.getId(), orderId);

        // 동일 paymentId로 환불 재요청 → 409
        mockMvc.perform(post("/api/refunds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"paymentId\":%d,\"reason\":\"중복 요청\"}",
                                payment.getId())))
                .andExpect(status().isConflict());
    }
}
