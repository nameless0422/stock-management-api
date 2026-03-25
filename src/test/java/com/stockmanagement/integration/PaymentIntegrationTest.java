package com.stockmanagement.integration;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.TossWebhookVerifier;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import com.stockmanagement.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Payment 통합 테스트.
 *
 * <p>TossPaymentsClient(외부 API)는 {@link MockBean}으로 대체하여
 * 실제 HTTP 요청 없이 결제 흐름을 검증한다.
 */
@DisplayName("Payment 통합 테스트")
class PaymentIntegrationTest extends AbstractIntegrationTest {

    @MockBean TossPaymentsClient tossPaymentsClient;
    @MockBean TossWebhookVerifier tossWebhookVerifier;

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;

    // ===== 공통 헬퍼 =====

    /**
     * 상품 등록 + 입고 + 주문 생성 후 orderId를 반환한다.
     * PaymentService.prepare()는 findByIdWithItems(INNER JOIN FETCH)를 사용하므로
     * 아이템이 있는 실제 주문이 필요하다.
     */
    private long createOrderViaApi(String adminToken, String userToken, long userId,
                                   String sku, int price, int quantity) throws Exception {
        String productBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":50}"))
                .andExpect(status().isOk());

        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"key-%s\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":%d,\"unitPrice\":%d}]}",
                                userId, sku + System.nanoTime(), productId, quantity, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(orderBody).path("data").path("id").asLong();
    }

    /** DONE 상태의 결제를 DB에 직접 저장하고 반환한다. */
    private Payment createDonePayment(long orderId, BigDecimal amount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .tossOrderId("toss-" + System.nanoTime())
                .amount(amount)
                .build();
        payment.approve("pk-test-" + System.nanoTime(), "카드",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    /** 주문 없이 payments 테이블에만 직접 저장 (조회 전용 테스트용). */
    private Order createRawOrder(long userId, BigDecimal amount) {
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(amount)
                .idempotencyKey("raw-order-" + System.nanoTime())
                .build();
        return orderRepository.save(order);
    }

    // ===== POST /api/payments/prepare =====

    @Nested
    @DisplayName("POST /api/payments/prepare")
    class Prepare {

        @Test
        @DisplayName("정상 주문 → 200, tossOrderId 포함")
        void prepareSuccess() throws Exception {
            String adminToken = createAdminAndLogin("admin_p1", "adminpass1!", "ap1@test.com");
            String userToken = signupAndLogin("buyer1", "password1!", "b1@test.com");
            long userId = userRepository.findByUsername("buyer1").orElseThrow().getId();
            long orderId = createOrderViaApi(adminToken, userToken, userId, "PREP-001", 10000, 3);

            mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"orderId\":%d,\"amount\":30000}", orderId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.tossOrderId").isString())
                    .andExpect(jsonPath("$.data.amount").value(30000));
        }

        @Test
        @DisplayName("금액 불일치 → 409")
        void prepareAmountMismatch() throws Exception {
            String adminToken = createAdminAndLogin("admin_p2", "adminpass2!", "ap2@test.com");
            String userToken = signupAndLogin("buyer2", "password2!", "b2@test.com");
            long userId = userRepository.findByUsername("buyer2").orElseThrow().getId();
            long orderId = createOrderViaApi(adminToken, userToken, userId, "PREP-002", 10000, 3);

            // 실제 금액(30000)과 다른 금액(20000) 전송 → 400 (PAYMENT_AMOUNT_MISMATCH)
            mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"orderId\":%d,\"amount\":20000}", orderId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID → 404")
        void prepareOrderNotFound() throws Exception {
            String userToken = signupAndLogin("buyer3", "password3!", "b3@test.com");

            mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":99999,\"amount\":10000}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("동일 PENDING 주문 중복 prepare → 200, 기존 tossOrderId 재사용")
        void prepareIdempotent() throws Exception {
            String adminToken = createAdminAndLogin("admin_p4", "adminpass4!", "ap4@test.com");
            String userToken = signupAndLogin("buyer4", "password4!", "b4@test.com");
            long userId = userRepository.findByUsername("buyer4").orElseThrow().getId();
            long orderId = createOrderViaApi(adminToken, userToken, userId, "PREP-004", 5000, 2);

            String firstBody = mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"orderId\":%d,\"amount\":10000}", orderId)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String secondBody = mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"orderId\":%d,\"amount\":10000}", orderId)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String firstId = objectMapper.readTree(firstBody).path("data").path("tossOrderId").asText();
            String secondId = objectMapper.readTree(secondBody).path("data").path("tossOrderId").asText();
            assert firstId.equals(secondId) : "idempotent prepare should return same tossOrderId";
        }
    }

    // ===== POST /api/payments/confirm =====

    @Nested
    @DisplayName("POST /api/payments/confirm")
    class Confirm {

        @Test
        @DisplayName("Toss 응답 DONE → 200, 결제 상태 DONE")
        void confirmSuccess() throws Exception {
            String adminToken = createAdminAndLogin("admin1", "adminpass1", "admin1@test.com");
            String userToken = signupAndLogin("buyer5", "password5", "b5@test.com");
            long userId = userRepository.findByUsername("buyer5").orElseThrow().getId();

            // 상품 등록 + 입고 + 주문 생성
            String productBody = mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"결제상품\",\"sku\":\"PAY-001\",\"price\":5000}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();

            mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":10}"))
                    .andExpect(status().isOk());

            String orderBody = mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"confirm-test-001\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":5000}]}",
                                    userId, productId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

            // Prepare 호출
            String prepareBody = mockMvc.perform(post("/api/payments/prepare")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"orderId\":%d,\"amount\":5000}", orderId)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String tossOrderId = objectMapper.readTree(prepareBody).path("data").path("tossOrderId").asText();

            // TossPaymentsClient mock
            TossConfirmResponse tossResponse = new TossConfirmResponse();
            // reflection으로 필드 설정 (Getter + NoArgsConstructor이므로 직접 세터 없음)
            setField(tossResponse, "paymentKey", "pk-test-12345");
            setField(tossResponse, "status", "DONE");
            setField(tossResponse, "method", "카드");
            setField(tossResponse, "requestedAt", "2025-01-01T00:00:00+09:00");
            setField(tossResponse, "approvedAt", "2025-01-01T00:00:01+09:00");
            given(tossPaymentsClient.confirm(any())).willReturn(tossResponse);

            mockMvc.perform(post("/api/payments/confirm")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"paymentKey\":\"pk-test-12345\",\"tossOrderId\":\"%s\",\"amount\":5000}",
                                    tossOrderId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DONE"))
                    .andExpect(jsonPath("$.data.paymentKey").value("pk-test-12345"));
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    // ===== GET /api/payments/order/{orderId} =====

    @Nested
    @DisplayName("GET /api/payments/order/{orderId}")
    class GetByOrderId {

        @Test
        @DisplayName("결제 없는 주문 → 200, data 필드 없음")
        void noPendingPayment() throws Exception {
            String userToken = signupAndLogin("buyer6", "password6!", "b6@test.com");
            long userId = userRepository.findByUsername("buyer6").orElseThrow().getId();
            Order order = createRawOrder(userId, BigDecimal.valueOf(10_000));

            // ApiResponse@JsonInclude(NON_NULL): data=null이면 data 필드 자체가 직렬화되지 않음
            mockMvc.perform(get("/api/payments/order/" + order.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("결제가 존재하면 → 200, 결제 정보 반환")
        void paymentExists() throws Exception {
            String userToken = signupAndLogin("buyer7", "password7!", "b7@test.com");
            long userId = userRepository.findByUsername("buyer7").orElseThrow().getId();
            Order order = createRawOrder(userId, BigDecimal.valueOf(15_000));
            Payment payment = createDonePayment(order.getId(), BigDecimal.valueOf(15_000));

            mockMvc.perform(get("/api/payments/order/" + order.getId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(payment.getId()))
                    .andExpect(jsonPath("$.data.status").value("DONE"));
        }
    }

    // ===== GET /api/payments/{paymentKey} =====

    @Nested
    @DisplayName("GET /api/payments/{paymentKey}")
    class GetByPaymentKey {

        @Test
        @DisplayName("존재하는 paymentKey → 200")
        void getByExistingKey() throws Exception {
            String userToken = signupAndLogin("buyer8", "password8!", "b8@test.com");
            long userId = userRepository.findByUsername("buyer8").orElseThrow().getId();
            Order order = createRawOrder(userId, BigDecimal.valueOf(8_000));
            Payment payment = createDonePayment(order.getId(), BigDecimal.valueOf(8_000));

            mockMvc.perform(get("/api/payments/" + payment.getPaymentKey())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentKey").value(payment.getPaymentKey()));
        }

        @Test
        @DisplayName("없는 paymentKey → 404")
        void getByMissingKey() throws Exception {
            String userToken = signupAndLogin("buyer9", "password9!", "b9@test.com");

            mockMvc.perform(get("/api/payments/nonexistent-key")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== POST /api/payments/webhook =====

    @Nested
    @DisplayName("POST /api/payments/webhook (공개 엔드포인트)")
    class Webhook {

        @Test
        @DisplayName("올바른 서명 → 200")
        void webhookSuccess() throws Exception {
            doNothing().when(tossWebhookVerifier).verify(anyString(), anyString());

            mockMvc.perform(post("/api/payments/webhook")
                            .header("Toss-Signature", "valid-sig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                                    "\"data\":{\"orderId\":\"toss-order-1\",\"status\":\"DONE\"}}"))
                    .andExpect(status().isOk());
        }
    }
}
