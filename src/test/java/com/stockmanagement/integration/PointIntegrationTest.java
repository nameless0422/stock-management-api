package com.stockmanagement.integration;

import com.stockmanagement.domain.point.entity.UserPoint;
import com.stockmanagement.domain.point.repository.UserPointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Point 통합 테스트")
class PointIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserPointRepository userPointRepository;

    // ===== 공통 헬퍼 =====

    private long createProductAndReceive(String adminToken, String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
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

    /** signup() 시 생성된 UserPoint 레코드에 잔액을 추가한다. */
    private void seedPoints(long userId, long amount) {
        UserPoint up = userPointRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("UserPoint not found for userId=" + userId));
        up.earn(amount);
        userPointRepository.save(up);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("포인트 잔액 조회 → 초기 0")
    void getBalance_initial() throws Exception {
        String userToken = signupAndLogin("user1", "password1", "u1@test.com");

        mockMvc.perform(get("/api/points/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("포인트 잔액 조회 → 사전 적립 후 잔액 반환")
    void getBalance_withPoints() throws Exception {
        String userToken = signupAndLogin("user2", "password1", "u2@test.com");
        long userId = userRepository.findByUsername("user2").orElseThrow().getId();
        seedPoints(userId, 5000);

        mockMvc.perform(get("/api/points/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(5000));
    }

    @Test
    @DisplayName("포인트 이력 조회 → 초기 빈 목록")
    void getHistory_empty() throws Exception {
        String userToken = signupAndLogin("user3", "password1", "u3@test.com");

        mockMvc.perform(get("/api/points/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("포인트 사용하여 주문 생성 → usedPoints 반영")
    void createOrder_withPoints() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-PT1", 20000, 10);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        seedPoints(userId, 3000);

        // 3000 포인트 사용하여 20000 상품 주문
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"usePoints\":3000," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":20000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.usedPoints").value(3000));

        // 포인트 차감 확인
        mockMvc.perform(get("/api/points/balance")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("포인트 부족 상태로 주문 생성 → 400 INSUFFICIENT_POINTS")
    void createOrder_insufficientPoints() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-PT2", 10000, 10);

        String userToken = signupAndLogin("buyer2", "password1", "b2@test.com");
        long userId = userRepository.findByUsername("buyer2").orElseThrow().getId();
        seedPoints(userId, 500);

        // 잔액(500)보다 많은 포인트(1000) 사용 시도 → 400
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"usePoints\":1000," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isBadRequest());
    }
}
