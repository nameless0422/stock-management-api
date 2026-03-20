package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Rate Limit(@RateLimit) 통합 테스트.
 *
 * <p>실제 Redis Testcontainer를 통해 요청 횟수 추적 및 한도 초과 응답(429)을 검증한다.
 */
@DisplayName("Rate Limit 통합 테스트")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("주문 생성 rate limit — 한도(10회/분) 초과 시 429 반환")
    void orderCreate_rateLimitExceeded_returns429() throws Exception {
        // 사용자마다 별도의 rate limit 카운터를 사용하므로 고유 사용자 생성
        String adminToken = createAdminAndLogin("rl-admin", "adminpass1", "rl-admin@example.com");

        // 상품 생성 + 충분한 재고 확보 (주문 10건 × 1개 = 10개 필요)
        String productBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RL테스트상품\",\"sku\":\"RL-SKU-001\",\"price\":10000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":20}"))
                .andExpect(status().isOk());

        String userToken = signupAndLogin("rl-buyer", "buyerpass1", "rl-buyer@example.com");
        long buyerId = userRepository.findByUsername("rl-buyer").orElseThrow().getId();

        // 한도 이내 요청(10회) — 모두 성공해야 한다
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"rl-test-%d\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                    buyerId, i, productId)))
                    .andExpect(status().isCreated());
        }

        // 11번째 요청 — rate limit 초과 → 429
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"rl-test-11\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                buyerId, productId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("결제 확정 rate limit — 사용자별로 독립 카운터 유지")
    void differentUsers_haveSeparateRateLimitCounters() throws Exception {
        // userA와 userB는 rate limit 카운터를 공유하지 않음을 검증
        String userAToken = signupAndLogin("rl-userA", "passA12345", "usera@example.com");
        String userBToken = signupAndLogin("rl-userB", "passB12345", "userb@example.com");

        String adminToken = createAdminAndLogin("rl-admin2", "adminpass1", "rl-admin2@example.com");
        String productBody = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RL분리상품\",\"sku\":\"RL-SKU-002\",\"price\":10000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":30}"))
                .andExpect(status().isOk());

        long userAId = userRepository.findByUsername("rl-userA").orElseThrow().getId();
        long userBId = userRepository.findByUsername("rl-userB").orElseThrow().getId();

        // userA 10회 주문 (한도 도달)
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"rl-a-test-%d\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                    userAId, i, productId)))
                    .andExpect(status().isCreated());
        }

        // userA 11번째 — 429
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"rl-a-test-11\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                userAId, productId)))
                .andExpect(status().isTooManyRequests());

        // userB는 다른 카운터 → 첫 요청이므로 정상 처리
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"rl-b-test-1\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                userBId, productId)))
                .andExpect(status().isCreated());
    }
}
