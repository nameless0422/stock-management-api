package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Wishlist 통합 테스트")
class WishlistIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    private long createProduct(String adminToken, String sku, int price) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("위시리스트 추가 → 201, productId 확인")
    void addToWishlist_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-WL1", 15000);

        String userToken = signupAndLogin("user1", "password1", "u1@test.com");
        mockMvc.perform(post("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(productId));
    }

    @Test
    @DisplayName("중복 추가 → 409 WISHLIST_ALREADY_EXISTS")
    void addToWishlist_duplicate() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-WL2", 15000);

        String userToken = signupAndLogin("user2", "password1", "u2@test.com");
        mockMvc.perform(post("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // 두 번째 추가 → 409
        mockMvc.perform(post("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("위시리스트 목록 조회 → 추가한 상품 포함")
    void getWishlist() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-WL3", 20000);

        String userToken = signupAndLogin("user3", "password1", "u3@test.com");
        mockMvc.perform(post("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wishlist")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(productId));
    }

    @Test
    @DisplayName("위시리스트 삭제 → 204, 이후 목록 비어 있음")
    void removeFromWishlist_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-WL4", 25000);

        String userToken = signupAndLogin("user4", "password1", "u4@test.com");
        mockMvc.perform(post("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // 삭제 후 목록 비어 있음 확인
        mockMvc.perform(get("/api/wishlist")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("없는 항목 삭제 → 404 WISHLIST_ITEM_NOT_FOUND")
    void removeFromWishlist_notFound() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-WL5", 10000);

        String userToken = signupAndLogin("user5", "password1", "u5@test.com");
        mockMvc.perform(delete("/api/wishlist/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }
}
