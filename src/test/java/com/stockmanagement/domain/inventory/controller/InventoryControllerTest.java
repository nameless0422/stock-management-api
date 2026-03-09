package com.stockmanagement.domain.inventory.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.inventory.dto.InventoryResponse;
import com.stockmanagement.domain.inventory.service.InventoryService;
import com.stockmanagement.security.JwtTokenProvider;
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

    // ===== GET /api/inventory/{productId} =====

    @Nested
    @DisplayName("GET /api/inventory/{productId}")
    class GetByProductId {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 재고 현황 조회 → 200")
        void returnsInventory() throws Exception {
            given(inventoryService.getByProductId(1L)).willReturn(mock(InventoryResponse.class));

            mockMvc.perform(get("/api/inventory/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/inventory/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 상품 재고 조회 → 404")
        void returnsNotFound() throws Exception {
            given(inventoryService.getByProductId(999L))
                    .willThrow(new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

            mockMvc.perform(get("/api/inventory/999"))
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
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/inventory/1/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
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
