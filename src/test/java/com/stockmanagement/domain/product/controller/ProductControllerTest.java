package com.stockmanagement.domain.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.service.ProductService;
import com.stockmanagement.security.JwtTokenProvider;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductController 단위 테스트")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    // ===== POST /api/products =====

    @Nested
    @DisplayName("POST /api/products")
    class Create {

        private static final String VALID_JSON =
                "{\"name\":\"테스트상품\",\"sku\":\"SKU-001\",\"price\":10000}";

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 상품 등록 성공 → 201")
        void createsProduct() throws Exception {
            given(productService.create(any())).willReturn(mock(ProductResponse.class));

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("필수 필드(name, sku, price) 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== GET /api/products/{id} =====

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetById {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 상품 단건 조회 → 200")
        void returnsProduct() throws Exception {
            given(productService.getById(1L)).willReturn(mock(ProductResponse.class));

            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 상품 → 404")
        void returnsNotFound() throws Exception {
            given(productService.getById(999L))
                    .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            mockMvc.perform(get("/api/products/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/products =====

    @Nested
    @DisplayName("GET /api/products")
    class GetList {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 상품 목록 페이징 조회 → 200")
        void returnsList() throws Exception {
            given(productService.getList(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== PUT /api/products/{id} =====

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class Update {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 상품 수정 성공 → 200")
        void updatesProduct() throws Exception {
            given(productService.update(eq(1L), any())).willReturn(mock(ProductResponse.class));

            mockMvc.perform(put("/api/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"수정상품\",\"price\":20000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(put("/api/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== DELETE /api/products/{id} =====

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class Delete {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 상품 삭제 성공 → 204")
        void deletesProduct() throws Exception {
            mockMvc.perform(delete("/api/products/1"))
                    .andExpect(status().isNoContent());

            verify(productService).delete(1L);
        }

        @Test
        @WithMockUser
        @DisplayName("USER — ADMIN 전용 → 403")
        void forbiddenForUser() throws Exception {
            mockMvc.perform(delete("/api/products/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
