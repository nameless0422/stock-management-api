package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("RestockNotification 통합 테스트")
class RestockNotificationIntegrationTest extends AbstractIntegrationTest {

    private long createProduct(String adminToken, String sku, int price) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("재입고 알림 신청 → 201, productId 확인")
    void subscribe_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-RN1", 15000);

        String userToken = signupAndLogin("user1", "Password1!", "u1@test.com");
        mockMvc.perform(post("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productName").exists());
    }

    @Test
    @DisplayName("중복 신청 → 409 RESTOCK_ALREADY_SUBSCRIBED")
    void subscribe_duplicate() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-RN2", 15000);

        String userToken = signupAndLogin("user2", "Password1!", "u2@test.com");
        mockMvc.perform(post("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("알림 목록 조회 → 신청한 상품 포함")
    void getMyNotifications() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-RN3", 20000);

        String userToken = signupAndLogin("user3", "Password1!", "u3@test.com");
        mockMvc.perform(post("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/me/restock-notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(productId));
    }

    @Test
    @DisplayName("알림 취소 → 204, 이후 목록 비어 있음")
    void unsubscribe_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProduct(adminToken, "SKU-RN4", 25000);

        String userToken = signupAndLogin("user4", "Password1!", "u4@test.com");
        mockMvc.perform(post("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/products/" + productId + "/restock-notify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/restock-notifications")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("인증 없이 접근 → 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(post("/api/products/1/restock-notify"))
                .andExpect(status().isUnauthorized());
    }
}
