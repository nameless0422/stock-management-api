package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 관리자 API 통합 테스트.
 *
 * <p>관리자 전용 엔드포인트의 인가 제어 및 기본 동작을 검증한다.
 * 배치 통계 데이터가 없어도 빈 목록을 반환하는지 확인한다.
 */
@DisplayName("Admin 통합 테스트")
class AdminIntegrationTest extends AbstractIntegrationTest {

    // ===== GET /api/admin/dashboard =====

    @Nested
    @DisplayName("GET /api/admin/dashboard")
    class Dashboard {

        @Test
        @DisplayName("ADMIN — 대시보드 조회 → 200, 통계 필드 포함")
        void adminCanGetDashboard() throws Exception {
            String adminToken = createAdminAndLogin("admin1", "adminpass1", "admin1@test.com");

            mockMvc.perform(get("/api/admin/dashboard")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalOrders").isNumber())
                    .andExpect(jsonPath("$.data.totalUsers").isNumber())
                    .andExpect(jsonPath("$.data.lowStockItems").isArray());
        }

        @Test
        @DisplayName("USER — ADMIN 전용 → 403")
        void userCannotGetDashboard() throws Exception {
            String userToken = signupAndLogin("user1", "password1", "user1@test.com");

            mockMvc.perform(get("/api/admin/dashboard")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticatedForbidden() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== GET /api/admin/users =====

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetUsers {

        @Test
        @DisplayName("ADMIN — 전체 사용자 목록 조회 → 200, 페이징 응답")
        void adminCanGetUsers() throws Exception {
            String adminToken = createAdminAndLogin("admin2", "adminpass2", "admin2@test.com");
            signupAndLogin("userA", "password1", "a@test.com");
            signupAndLogin("userB", "password2", "b@test.com");

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(3)); // admin + 2 users
        }

        @Test
        @DisplayName("ADMIN — search 파라미터로 사용자 검색 → 200, 필터 적용")
        void adminCanSearchUsers() throws Exception {
            String adminToken = createAdminAndLogin("admin3", "adminpass3", "admin3@test.com");
            signupAndLogin("findme", "password1", "findme@test.com");
            signupAndLogin("other", "password2", "other@test.com");

            mockMvc.perform(get("/api/admin/users?search=findme")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].username").value("findme"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ===== PATCH /api/admin/users/{id}/role =====

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/role")
    class UpdateRole {

        @Test
        @DisplayName("ADMIN — USER → ADMIN 권한 변경 → 200, role=ADMIN")
        void adminCanUpdateRole() throws Exception {
            String adminToken = createAdminAndLogin("admin4", "adminpass4", "admin4@test.com");
            signupAndLogin("promote_me", "password1", "promote@test.com");
            long userId = userRepository.findByUsername("promote_me").orElseThrow().getId();

            mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 ID → 404")
        void updateRoleNotFound() throws Exception {
            String adminToken = createAdminAndLogin("admin5", "adminpass5", "admin5@test.com");

            mockMvc.perform(patch("/api/admin/users/99999/role")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== GET /api/admin/orders =====

    @Nested
    @DisplayName("GET /api/admin/orders")
    class GetOrders {

        @Test
        @DisplayName("ADMIN — 전체 주문 목록 → 200, 페이징 응답")
        void adminCanGetOrders() throws Exception {
            String adminToken = createAdminAndLogin("admin6", "adminpass6", "admin6@test.com");

            mockMvc.perform(get("/api/admin/orders")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("status 필터 파라미터 → 200")
        void adminCanFilterByStatus() throws Exception {
            String adminToken = createAdminAndLogin("admin7", "adminpass7", "admin7@test.com");

            mockMvc.perform(get("/api/admin/orders?status=PENDING")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    // ===== GET /api/admin/products =====

    @Nested
    @DisplayName("GET /api/admin/products")
    class GetProducts {

        @Test
        @DisplayName("ADMIN — 전체 상품 목록 → 200, 페이징 응답")
        void adminCanGetProducts() throws Exception {
            String adminToken = createAdminAndLogin("admin8", "adminpass8", "admin8@test.com");

            // 상품 등록
            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"테스트노트북\",\"sku\":\"NB-001\",\"price\":1000000}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/admin/products")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ===== GET /api/admin/stats/orders =====

    @Nested
    @DisplayName("GET /api/admin/stats/orders")
    class OrderStats {

        @Test
        @DisplayName("ADMIN — 기간별 통계 조회 → 200, 빈 목록 (데이터 없음)")
        void adminCanGetOrderStats() throws Exception {
            String adminToken = createAdminAndLogin("admin9", "adminpass9", "admin9@test.com");

            mockMvc.perform(get("/api/admin/stats/orders?from=2025-01-01&to=2025-01-31")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ===== GET /api/admin/stats/inventory =====

    @Nested
    @DisplayName("GET /api/admin/stats/inventory")
    class InventoryStats {

        @Test
        @DisplayName("ADMIN — 재고 스냅샷 조회 → 200, 빈 목록 (데이터 없음)")
        void adminCanGetInventorySnapshot() throws Exception {
            String adminToken = createAdminAndLogin("admin10", "adminpass10", "admin10@test.com");

            mockMvc.perform(get("/api/admin/stats/inventory?date=2025-01-01")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
