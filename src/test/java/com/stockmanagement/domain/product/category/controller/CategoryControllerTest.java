package com.stockmanagement.domain.product.category.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.RefreshTokenStore;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.service.CategoryService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@DisplayName("CategoryController 단위 테스트")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    @MockBean
    private RefreshTokenStore refreshTokenStore;

    private static final String VALID_CREATE_JSON = "{\"name\":\"전자\"}";

    // ===== POST /api/categories =====

    @Nested
    @DisplayName("POST /api/categories")
    class Create {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 카테고리 생성 성공 → 201")
        void createsCategory() throws Exception {
            given(categoryService.create(any())).willReturn(mock(CategoryResponse.class));

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void forbiddenWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_CREATE_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/categories =====

    @Nested
    @DisplayName("GET /api/categories")
    class GetList {

        @Test
        @DisplayName("인증 없음 → 200 (공개 엔드포인트)")
        void allowsUnauthenticated() throws Exception {
            given(categoryService.getList()).willReturn(List.of());

            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== GET /api/categories/tree =====

    @Nested
    @DisplayName("GET /api/categories/tree")
    class GetTree {

        @Test
        @DisplayName("인증 없음 → 200 (공개 엔드포인트)")
        void allowsUnauthenticated() throws Exception {
            given(categoryService.getTree()).willReturn(List.of());

            mockMvc.perform(get("/api/categories/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== DELETE /api/categories/{id} =====

    @Nested
    @DisplayName("DELETE /api/categories/{id}")
    class Delete {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 카테고리 삭제 → 204")
        void deletesCategory() throws Exception {
            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(delete("/api/categories/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
