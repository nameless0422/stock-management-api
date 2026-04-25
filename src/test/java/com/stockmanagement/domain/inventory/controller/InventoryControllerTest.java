package com.stockmanagement.domain.inventory.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.dto.CursorPage;
import org.springframework.data.domain.Page;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
@DisplayName("InventoryController 단위 테스트")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    // ===== GET /api/inventory =====

    @Nested
    @DisplayName("GET /api/inventory")
    class Search {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 필터 없이 조회 → 200, Page 구조 반환")
        void returnsPage() throws Exception {
            given(inventoryService.search(any(), any())).willReturn(Page.empty());

            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("status=LOW_STOCK 파라미터 → 200")
        void filterByStatus() throws Exception {
            given(inventoryService.search(any(), any())).willReturn(Page.empty());

            mockMvc.perform(get("/api/inventory").param("status", "LOW_STOCK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("productName + category + 가용 범위 복합 파라미터 → 200")
        void filterByMultipleParams() throws Exception {
            given(inventoryService.search(any(), any())).willReturn(Page.empty());

            mockMvc.perform(get("/api/inventory")
                            .param("productName", "노트북")
                            .param("category", "전자기기")
                            .param("minAvailable", "5")
                            .param("maxAvailable", "100")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("잘못된 status 값 → 400")
        void invalidStatusParam_returns400() throws Exception {
            mockMvc.perform(get("/api/inventory").param("status", "INVALID_STATUS"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== GET /api/inventory/{productId} =====

    @Nested
    @DisplayName("GET /api/inventory/{productId}")
    class GetByProductId {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 재고 현황 조회 → 200")
        void returnsInventory() throws Exception {
            given(inventoryService.getByProductId(1L)).willReturn(mock(InventoryResponse.class));

            mockMvc.perform(get("/api/inventory/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/inventory/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("존재하지 않는 상품 재고 조회 → 404")
        void returnsNotFound() throws Exception {
            given(inventoryService.getByProductId(999L))
                    .willThrow(new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

            mockMvc.perform(get("/api/inventory/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/inventory/{productId}/transactions =====

    @Nested
    @DisplayName("GET /api/inventory/{productId}/transactions")
    class GetTransactions {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 이력 조회 → 200 (커서 페이지)")
        void returnsTransactions() throws Exception {
            given(inventoryService.getTransactions(eq(1L), any(), eq(20)))
                    .willReturn(CursorPage.of(java.util.List.of(), 20, t -> t.getId()));

            mockMvc.perform(get("/api/inventory/1/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/inventory/1/transactions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("재고가 없는 상품 이력 조회 → 404")
        void returnsNotFoundWhenInventoryMissing() throws Exception {
            given(inventoryService.getTransactions(eq(999L), any(), eq(20)))
                    .willThrow(new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

            mockMvc.perform(get("/api/inventory/999/transactions"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== POST /api/inventory/{productId}/receive =====

    @Nested
    @DisplayName("POST /api/inventory/{productId}/receive")
    class Receive {

        private static final String VALID_JSON = "{\"quantity\":10}";

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 입고 처리 성공 → 200")
        void receivesInventory() throws Exception {
            given(inventoryService.receive(eq(1L), any())).willReturn(mock(InventoryResponse.class));

            mockMvc.perform(post("/api/inventory/1/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(post("/api/inventory/1/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/inventory/1/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("quantity 누락 또는 0 이하 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/inventory/1/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
