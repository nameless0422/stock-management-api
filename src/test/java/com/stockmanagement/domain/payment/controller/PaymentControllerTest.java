package com.stockmanagement.domain.payment.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.payment.dto.PaymentPrepareResponse;
import com.stockmanagement.domain.payment.dto.PaymentResponse;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.payment.infrastructure.TossWebhookVerifier;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@DisplayName("PaymentController 단위 테스트")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    @MockBean
    private UserService userService;

    @MockBean
    private TossWebhookVerifier webhookVerifier;

    // ===== POST /api/payments/prepare =====

    @Nested
    @DisplayName("POST /api/payments/prepare")
    class Prepare {

        private static final String VALID_JSON = "{\"orderId\":1,\"amount\":10000}";

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 결제 준비 성공 → 200")
        void preparesPayment() throws Exception {
            PaymentPrepareResponse response =
                    new PaymentPrepareResponse("toss-order-001", BigDecimal.valueOf(10000), "상품명");
            given(paymentService.prepare(any(), any())).willReturn(response);

            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("필수 필드(orderId, amount) 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/payments/prepare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== POST /api/payments/confirm =====

    @Nested
    @DisplayName("POST /api/payments/confirm")
    class Confirm {

        private static final String VALID_JSON =
                "{\"paymentKey\":\"pk-test\",\"tossOrderId\":\"toss-001\",\"amount\":10000}";

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 결제 승인 성공 → 200")
        void confirmsPayment() throws Exception {
            given(paymentService.confirm(any(), any())).willReturn(mock(PaymentResponse.class));

            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("결제 금액 불일치 → 400")
        void amountMismatch() throws Exception {
            given(paymentService.confirm(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== POST /api/payments/{paymentKey}/cancel =====

    @Nested
    @DisplayName("POST /api/payments/{paymentKey}/cancel")
    class CancelPayment {

        private static final String VALID_JSON = "{\"cancelReason\":\"고객 변심\"}";

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 결제 취소 성공 → 200")
        void cancelsPayment() throws Exception {
            given(paymentService.cancel(eq("pk-test"), any(), any(), anyBoolean())).willReturn(mock(PaymentResponse.class));

            mockMvc.perform(post("/api/payments/pk-test/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/payments/pk-test/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("cancelReason 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/payments/pk-test/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== POST /api/payments/webhook =====

    @Nested
    @DisplayName("POST /api/payments/webhook")
    class Webhook {

        private static final String WEBHOOK_JSON =
                "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"data\":{\"paymentKey\":\"pk-test\",\"orderId\":\"toss-001\",\"status\":\"DONE\"}}";

        @Test
        @DisplayName("인증 없이도 webhook 수신 가능 (PUBLIC) → 200")
        void receivesWebhookWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/payments/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(WEBHOOK_JSON))
                    .andExpect(status().isOk());
        }
    }

    // ===== GET /api/payments/order/{orderId} =====

    @Nested
    @DisplayName("GET /api/payments/order/{orderId}")
    class GetByOrderId {

        @Test
        @WithMockUser
        @DisplayName("본인 주문 결제 조회 → 200")
        void returnsPayment() throws Exception {
            given(paymentService.getByOrderId(anyLong(), any(), anyBoolean()))
                    .willReturn(java.util.Optional.of(mock(PaymentResponse.class)));

            mockMvc.perform(get("/api/payments/order/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("결제 없는 주문 → 200, data null")
        void returnsNullWhenNoPayment() throws Exception {
            given(paymentService.getByOrderId(anyLong(), any(), anyBoolean()))
                    .willReturn(java.util.Optional.empty());

            mockMvc.perform(get("/api/payments/order/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/payments/order/1"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/payments/{paymentKey} =====

    @Nested
    @DisplayName("GET /api/payments/{paymentKey}")
    class GetByPaymentKey {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 결제 내역 조회 → 200")
        void returnsPayment() throws Exception {
            given(paymentService.getByPaymentKey(eq("pk-test"), any(), anyBoolean()))
                    .willReturn(mock(PaymentResponse.class));

            mockMvc.perform(get("/api/payments/pk-test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/payments/pk-test"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 결제 → 404")
        void returnsNotFound() throws Exception {
            given(paymentService.getByPaymentKey(eq("unknown"), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            mockMvc.perform(get("/api/payments/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @WithMockUser
        @DisplayName("타인 결제 조회 시도 → 403")
        void deniesAccessForNonOwner() throws Exception {
            given(paymentService.getByPaymentKey(eq("pk-test"), any(), anyBoolean()))
                    .willThrow(new BusinessException(ErrorCode.ORDER_ACCESS_DENIED));

            mockMvc.perform(get("/api/payments/pk-test"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
