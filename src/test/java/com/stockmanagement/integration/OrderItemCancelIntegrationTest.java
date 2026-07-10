package com.stockmanagement.integration;

import com.stockmanagement.domain.payment.infrastructure.TossPaymentsClient;
import com.stockmanagement.domain.payment.infrastructure.TossWebhookVerifier;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 아이템 부분 취소 통합 테스트.
 *
 * <p>cancelItems()는 분산 락 + Toss 부분 환불(외부 HTTP)을 사이에 둔 Short TX 2개로 구성된다.
 * 회귀 검증 대상 (#200): Short TX가 프록시를 경유하지 않으면(self-invocation)
 * 아이템 취소·주문 상태 전이가 DB에 영속화되지 않아 동일 아이템 재취소로
 * Toss 중복 환불·재고 이중 해제가 가능해진다.
 *
 * <p>TossPaymentsClient(외부 API)는 {@link MockBean}으로 대체한다.
 */
@DisplayName("주문 아이템 부분 취소 통합 테스트")
class OrderItemCancelIntegrationTest extends AbstractIntegrationTest {

    @MockBean TossPaymentsClient tossPaymentsClient;
    @MockBean TossWebhookVerifier tossWebhookVerifier;

    @Test
    @DisplayName("부분 취소 → 상태 영속화 + 재고 해제, 동일 아이템 재취소 409, 전체 취소 시 CANCELLED")
    void partialCancel_persistsState_andRejectsDoubleCancel() throws Exception {
        // ===== 준비: 상품 2개 등록 + 입고 =====
        String adminToken = createAdminAndLogin("admin_ic1", "adminpass1!", "aic1@test.com");
        String userToken = signupAndLogin("buyer_ic1", "Password1!", "bic1@test.com");
        long userId = userRepository.findByUsername("buyer_ic1").orElseThrow().getId();

        TestProduct productA = createProductWithStock(adminToken, "ITEM-CXL-A", 10_000, 10);
        TestProduct productB = createProductWithStock(adminToken, "ITEM-CXL-B", 5_000, 10);
        long variantA = productA.variantId();
        long variantB = productB.variantId();

        // ===== 주문 생성 (A x1 + B x2 = 20000) =====
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"item-cancel-001\",\"items\":[" +
                                "{\"productId\":%d,\"variantId\":%d,\"quantity\":1,\"unitPrice\":10000}," +
                                "{\"productId\":%d,\"variantId\":%d,\"quantity\":2,\"unitPrice\":5000}]}",
                                userId, productA.productId(), variantA, productB.productId(), variantB)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

        // ===== 결제 확정 (Toss confirm mock) → CONFIRMED =====
        confirmPayment(userToken, orderId, 20_000);

        long itemA = findItemIdByVariant(userToken, orderId, variantA);
        long itemB = findItemIdByVariant(userToken, orderId, variantB);

        // 확정 후 재고: A allocated=1 (available 9)
        assertInventory(adminToken, variantA, 1, 9);

        // ===== 1차 부분 취소 (아이템 A) → PARTIAL_CANCELLED =====
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items/cancel")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"itemIds\":[%d],\"reason\":\"단순 변심\"}", itemA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PARTIAL_CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(10000));

        // 상태 영속화 검증 — 주문 PARTIAL_CANCELLED, 아이템 A만 CANCELLED (#200 회귀 핵심)
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL_CANCELLED"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + itemA + ")].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + itemB + ")].status").value("ACTIVE"));

        // 재고 해제 검증 — A allocated 0, available 복원
        assertInventory(adminToken, variantA, 0, 10);

        // ===== 동일 아이템 재취소 → 409, Toss 재환불 없음 =====
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items/cancel")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"itemIds\":[%d]}", itemA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Toss 부분 환불은 1차 취소 때 1회만 호출되어야 한다 (중복 환불 방지)
        then(tossPaymentsClient).should(times(1)).cancel(any(), any());

        // 재고 이중 해제 없음
        assertInventory(adminToken, variantA, 0, 10);

        // ===== 남은 아이템 B 취소 → 전체 CANCELLED =====
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items/cancel")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"itemIds\":[%d]}", itemB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(10000));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertInventory(adminToken, variantB, 0, 10);
    }

    @Test
    @DisplayName("PENDING 주문 부분 취소 시도 → 409")
    void partialCancel_pendingOrder_409() throws Exception {
        String adminToken = createAdminAndLogin("admin_ic2", "adminpass2!", "aic2@test.com");
        String userToken = signupAndLogin("buyer_ic2", "Password1!", "bic2@test.com");
        long userId = userRepository.findByUsername("buyer_ic2").orElseThrow().getId();

        TestProduct product = createProductWithStock(adminToken, "ITEM-CXL-C", 3_000, 5);
        long variantId = product.variantId();

        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"item-cancel-002\",\"items\":[" +
                                "{\"productId\":%d,\"variantId\":%d,\"quantity\":1,\"unitPrice\":3000}]}",
                                userId, product.productId(), variantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();
        long itemId = findItemIdByVariant(userToken, orderId, variantId);

        // 결제 전(PENDING) 부분 취소 불가
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items/cancel")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"itemIds\":[%d]}", itemId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ===== 헬퍼 =====

    /** 테스트용 상품 식별자 홀더. */
    private record TestProduct(long productId, long variantId) {}

    /** 상품 등록 + 입고 후 productId·기본 variantId를 반환한다. */
    private TestProduct createProductWithStock(String adminToken, String sku, int price, int quantity) throws Exception {
        String productBody = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();
        long variantId = getDefaultVariantId(productId);

        mockMvc.perform(post("/api/v1/inventory/variants/" + variantId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"quantity\":%d}", quantity)))
                .andExpect(status().isOk());
        return new TestProduct(productId, variantId);
    }

    /** prepare → Toss confirm(mock) → confirm 호출로 주문을 CONFIRMED 상태로 만든다. */
    private void confirmPayment(String userToken, long orderId, int amount) throws Exception {
        String prepareBody = mockMvc.perform(post("/api/v1/payments/prepare")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"orderId\":%d,\"amount\":%d}", orderId, amount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tossOrderId = objectMapper.readTree(prepareBody).path("data").path("tossOrderId").asText();

        TossConfirmResponse tossResponse = new TossConfirmResponse();
        setField(tossResponse, "paymentKey", "pk-item-cancel-" + orderId);
        setField(tossResponse, "status", "DONE");
        setField(tossResponse, "method", "카드");
        setField(tossResponse, "requestedAt", "2026-01-01T00:00:00+09:00");
        setField(tossResponse, "approvedAt", "2026-01-01T00:00:01+09:00");
        given(tossPaymentsClient.confirm(any())).willReturn(tossResponse);

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"paymentKey\":\"pk-item-cancel-%d\",\"tossOrderId\":\"%s\",\"amount\":%d}",
                                orderId, tossOrderId, amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    /** 주문 조회 응답에서 variantId에 해당하는 아이템 ID를 찾는다. */
    private long findItemIdByVariant(String userToken, long orderId, long variantId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var item : objectMapper.readTree(body).path("data").path("items")) {
            if (item.path("variantId").asLong() == variantId) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("variantId=" + variantId + " 아이템을 주문에서 찾을 수 없음");
    }

    /** 재고의 allocated·available 값을 검증한다. */
    private void assertInventory(String adminToken, long variantId, int allocated, int available) throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/" + variantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocated").value(allocated))
                .andExpect(jsonPath("$.data.available").value(available));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
