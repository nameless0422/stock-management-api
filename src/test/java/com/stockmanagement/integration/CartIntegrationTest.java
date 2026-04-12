package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Cart 통합 테스트")
class CartIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    private long createProductAndReceive(String adminToken, String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
        return productId;
    }

    @Test
    @DisplayName("상품 담기 → 장바구니 조회 → totalAmount 확인")
    void addItemAndGetCart() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-C1", 2000, 50);
        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");

        // 담기
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.totalAmount").value(6000));

        // 조회
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].productId").value(productId))
                .andExpect(jsonPath("$.data.totalAmount").value(6000));
    }

    @Test
    @DisplayName("동일 상품 다시 담기 → 수량 교체")
    void addSameItemUpdatesQuantity() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-C2", 1000, 50);
        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.totalAmount").value(5000));
    }

    @Test
    @DisplayName("특정 상품 제거 → 장바구니에서 삭제됨")
    void removeItem() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "admin@test.com");
        long p1 = createProductAndReceive(adminToken, "SKU-C3", 1000, 10);
        long p2 = createProductAndReceive(adminToken, "SKU-C4", 2000, 10);
        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p1 + ",\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p2 + ",\"quantity\":1}"))
                .andExpect(status().isOk());

        // p1 제거
        mockMvc.perform(delete("/api/cart/items/" + p1)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(p2));
    }

    @Test
    @DisplayName("장바구니 비우기 → items 없음")
    void clearCart() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-C5", 500, 20);
        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    @DisplayName("장바구니 → 주문 전환 → 재고 예약 확인 + 장바구니 비워짐")
    void checkout() throws Exception {
        String adminToken = createAdminAndLogin("admin", "pass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-C6", 3000, 20);
        String userToken = signupAndLogin("buyer", "password1", "buyer@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        // 담기
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk());

        // 주문 전환
        mockMvc.perform(post("/api/cart/checkout")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"cart-checkout-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(6000));

        // 장바구니 비워졌는지 확인
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        // 재고 예약 확인
        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserved").value(2))
                .andExpect(jsonPath("$.data.available").value(18));
    }

    @Test
    @DisplayName("빈 장바구니 주문 전환 → 400 Bad Request")
    void checkoutEmptyCart_returns400() throws Exception {
        signupAndLogin("buyer2", "password1", "buyer2@test.com");
        String userToken = login("buyer2", "password1");

        mockMvc.perform(post("/api/cart/checkout")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"cart-checkout-002\"}"))
                .andExpect(status().isBadRequest());
    }
}
