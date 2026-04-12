package com.stockmanagement.domain.admin.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.admin.dto.AdminOrderResponse;
import com.stockmanagement.domain.admin.dto.DashboardResponse;
import com.stockmanagement.domain.admin.service.AdminService;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.inventory.dto.DailyInventorySnapshotResponse;
import com.stockmanagement.domain.order.dto.DailyOrderStatsResponse;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminController 단위 테스트")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AdminService adminService;
    @MockBean private SystemSettingService systemSettingService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    // ===== GET /api/admin/dashboard =====

    @Nested
    @DisplayName("GET /api/admin/dashboard")
    class Dashboard {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 대시보드 조회 → 200")
        void adminGetsDashboard() throws Exception {
            given(adminService.getDashboard()).willReturn(mock(DashboardResponse.class));

            mockMvc.perform(get("/api/admin/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 대시보드 조회 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/admin/users =====

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetUsers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 사용자 목록 조회 → 200")
        void adminGetsUsers() throws Exception {
            given(adminService.getUsers(any(Pageable.class), isNull()))
                    .willReturn(new PageImpl<>(List.of(mock(UserResponse.class))));

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 사용자 목록 조회 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== PATCH /api/admin/users/{id}/role =====

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/role")
    class UpdateRole {

        private static final String ROLE_JSON = "{\"role\":\"ADMIN\"}";

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 권한 변경 → 200")
        void adminUpdatesRole() throws Exception {
            given(adminService.updateRole(any(), any())).willReturn(mock(UserResponse.class));

            mockMvc.perform(patch("/api/admin/users/1/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ROLE_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 권한 변경 → 403")
        void userForbidden() throws Exception {
            mockMvc.perform(patch("/api/admin/users/1/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ROLE_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/admin/orders =====

    @Nested
    @DisplayName("GET /api/admin/orders")
    class GetOrders {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 전체 주문 조회 → 200")
        void adminGetsOrders() throws Exception {
            given(adminService.getOrders(isNull(), isNull(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(mock(AdminOrderResponse.class))));

            mockMvc.perform(get("/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== GET /api/admin/stats/orders =====

    @Nested
    @DisplayName("GET /api/admin/stats/orders")
    class OrderStats {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 일별 주문 통계 → 200")
        void adminGetsOrderStats() throws Exception {
            given(adminService.getOrderStats(any(), any()))
                    .willReturn(List.of(mock(DailyOrderStatsResponse.class)));

            mockMvc.perform(get("/api/admin/stats/orders")
                            .param("from", "2025-01-01")
                            .param("to", "2025-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== GET /api/admin/stats/inventory =====

    @Nested
    @DisplayName("GET /api/admin/stats/inventory")
    class InventoryStats {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 재고 스냅샷 조회 → 200")
        void adminGetsInventorySnapshot() throws Exception {
            given(adminService.getInventorySnapshot(any()))
                    .willReturn(List.of(mock(DailyInventorySnapshotResponse.class)));

            mockMvc.perform(get("/api/admin/stats/inventory")
                            .param("date", "2025-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
