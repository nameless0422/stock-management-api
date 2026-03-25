package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 사용자 API 통합 테스트.
 *
 * <p>인증 관련 테스트는 {@link AuthIntegrationTest}에서 다루며,
 * 여기서는 내 주문 조회 및 회원 탈퇴 흐름을 검증한다.
 */
@DisplayName("User 통합 테스트")
class UserIntegrationTest extends AbstractIntegrationTest {

    // ===== GET /api/users/me/orders =====

    @Nested
    @DisplayName("GET /api/users/me/orders")
    class GetMyOrders {

        @Test
        @DisplayName("인증된 사용자 — 빈 주문 목록 → 200")
        void returnsEmptyOrders() throws Exception {
            String token = signupAndLogin("user1", "password1", "u1@test.com");

            mockMvc.perform(get("/api/users/me/orders")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticatedForbidden() throws Exception {
            mockMvc.perform(get("/api/users/me/orders"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== DELETE /api/users/me =====

    @Nested
    @DisplayName("DELETE /api/users/me (회원 탈퇴)")
    class Deactivate {

        @Test
        @DisplayName("탈퇴 → 200, 이후 로그인 불가 → 401")
        void deactivateAndLoginFails() throws Exception {
            String token = signupAndLogin("leaver", "password1", "leave@test.com");

            // 탈퇴 → 200
            mockMvc.perform(delete("/api/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 탈퇴 후 로그인 시도 → 401
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"leaver\",\"password\":\"password1\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticatedForbidden() throws Exception {
            mockMvc.perform(delete("/api/users/me"))
                    .andExpect(status().isForbidden());
        }
    }
}
