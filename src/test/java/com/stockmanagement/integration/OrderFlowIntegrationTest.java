package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Order 통합 플로우 테스트")
class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    /**
     * 핵심 플로우: 상품 등록 → 입고 → 주문 생성 → 재고 예약 확인 → 주문 취소 → 예약 해제 확인
     */
    @Test
    @DisplayName("주문 생성 → 재고 예약 → 주문 취소 → 예약 해제")
    void fullOrderFlow() throws Exception {
        // 1. ADMIN 생성 + 상품 등록
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        String createProductBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"테스트상품\",\"sku\":\"SKU-001\",\"price\":10000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createProductBody).path("data").path("id").asLong();

        // 2. 입고 처리 (onHand = 50)
        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHand").value(50))
                .andExpect(jsonPath("$.data.available").value(50));

        // 3. 일반 유저 생성 + 주문 생성 (수량 3, 단가 10000 — product.price와 일치)
        String userToken = signupAndLogin("buyer", "password123", "buyer@example.com");
        long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();

        String createOrderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"order-key-001\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":3,\"unitPrice\":10000}]}",
                                buyerId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(30000))
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(createOrderBody).path("data").path("id").asLong();

        // 4. 재고 예약 확인 (reserved = 3, available = 47)
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHand").value(50))
                .andExpect(jsonPath("$.data.reserved").value(3))
                .andExpect(jsonPath("$.data.available").value(47));

        // 5. 주문 취소
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 6. 재고 예약 해제 확인 (reserved = 0, available = 50 복원)
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserved").value(0))
                .andExpect(jsonPath("$.data.available").value(50));
    }

    @Test
    @DisplayName("단가 불일치 → 400")
    void createOrder_priceMismatch_400() throws Exception {
        // 상품 가격 10000으로 등록
        String adminToken = createAdminAndLogin("admin2", "adminpass2", "admin2@example.com");
        String createProductBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"가격테스트\",\"sku\":\"SKU-PRICE\",\"price\":10000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createProductBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("buyer2", "password123", "buyer2@example.com");
        long buyerId = userRepository.findByUsername("buyer2").orElseThrow().getId();

        // 단가 9999 ≠ 10000 → 400
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"price-mismatch-001\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":9999}]}",
                                buyerId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("재고 부족 → 409")
    void createOrder_insufficientStock_409() throws Exception {
        // 입고 5, 주문 10 → 재고 부족
        String adminToken = createAdminAndLogin("admin3", "adminpass3", "admin3@example.com");
        String createProductBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"재고부족상품\",\"sku\":\"SKU-LOW\",\"price\":5000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createProductBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("buyer3", "password123", "buyer3@example.com");
        long buyerId = userRepository.findByUsername("buyer3").orElseThrow().getId();

        // 가용 재고(5) 초과 주문(10) → 409
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"stock-001\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":10,\"unitPrice\":5000}]}",
                                buyerId, productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("멱등성 키 중복 요청 → 기존 주문 반환, 재고 이중 예약 없음")
    void createOrder_idempotent() throws Exception {
        String adminToken = createAdminAndLogin("admin4", "adminpass4", "admin4@example.com");
        String createProductBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"멱등성상품\",\"sku\":\"SKU-IDEM\",\"price\":2000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createProductBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("buyer4", "password123", "buyer4@example.com");
        long buyerId = userRepository.findByUsername("buyer4").orElseThrow().getId();

        String orderJson = String.format(
                "{\"userId\":%d,\"idempotencyKey\":\"idem-key-001\"," +
                "\"items\":[{\"productId\":%d,\"quantity\":2,\"unitPrice\":2000}]}",
                buyerId, productId);

        // 첫 번째 주문
        String firstResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long firstOrderId = objectMapper.readTree(firstResponse).path("data").path("id").asLong();

        // 동일 멱등성 키로 재요청 → 같은 주문 반환
        String secondResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long secondOrderId = objectMapper.readTree(secondResponse).path("data").path("id").asLong();

        // 동일한 주문 ID 반환 확인
        assertEquals(firstOrderId, secondOrderId);

        // 재고 이중 예약 없음 — reserved = 2 (중복 아님)
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserved").value(2))
                .andExpect(jsonPath("$.data.available").value(18));
    }

    @Test
    @DisplayName("CANCELLED 주문 재취소 시도 → 409")
    void cancelOrder_alreadyCancelled_409() throws Exception {
        String adminToken = createAdminAndLogin("admin5", "adminpass5", "admin5@example.com");
        String createProductBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"취소테스트\",\"sku\":\"SKU-CXL\",\"price\":3000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createProductBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("buyer5", "password123", "buyer5@example.com");
        long buyerId = userRepository.findByUsername("buyer5").orElseThrow().getId();

        // 주문 생성
        String createOrderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"cancel-test-001\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":3000}]}",
                                buyerId, productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(createOrderBody).path("data").path("id").asLong();

        // 첫 번째 취소 → 200
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // 이미 CANCELLED 주문 재취소 → 409
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
