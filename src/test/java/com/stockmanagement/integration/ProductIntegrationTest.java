package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Product 통합 테스트")
class ProductIntegrationTest extends AbstractIntegrationTest {

    // ===== POST /api/products =====

    @Nested
    @DisplayName("POST /api/products")
    class Create {

        @Test
        @DisplayName("ADMIN — 상품 등록 성공 → 201, status=ACTIVE")
        void createProduct_admin_201() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"테스트상품\",\"sku\":\"SKU-001\",\"price\":10000}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.sku").value("SKU-001"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("USER — ADMIN 전용 → 403")
        void createProduct_user_403() throws Exception {
            String userToken = signupAndLogin("normaluser", "password123", "user@example.com");

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"상품\",\"sku\":\"SKU-002\",\"price\":5000}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SKU 중복 → 409")
        void createProduct_duplicateSku_409() throws Exception {
            String adminToken = createAdminAndLogin("admin2", "adminpass2", "admin2@example.com");
            String productJson = "{\"name\":\"상품A\",\"sku\":\"SKU-DUP\",\"price\":10000}";

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(productJson))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"상품B\",\"sku\":\"SKU-DUP\",\"price\":10000}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/products/{id} =====

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetById {

        @Test
        @DisplayName("상품 단건 조회 → 200")
        void getProduct_200() throws Exception {
            String adminToken = createAdminAndLogin("admin3", "adminpass3", "admin3@example.com");
            String userToken = signupAndLogin("user3", "password123", "user3@example.com");

            String responseBody = mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"조회테스트\",\"sku\":\"SKU-GET\",\"price\":5000}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long productId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("조회테스트"))
                    .andExpect(jsonPath("$.data.price").value(5000));
        }

        @Test
        @DisplayName("존재하지 않는 상품 → 404")
        void getProduct_notFound_404() throws Exception {
            String userToken = signupAndLogin("user4", "password123", "user4@example.com");

            mockMvc.perform(get("/api/products/99999")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== PUT /api/products/{id} =====

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class Update {

        @Test
        @DisplayName("ADMIN — 상품 수정 성공 → 200")
        void updateProduct_admin_200() throws Exception {
            String adminToken = createAdminAndLogin("admin5", "adminpass5", "admin5@example.com");

            String createBody = mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"원래이름\",\"sku\":\"SKU-UPD\",\"price\":10000}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long productId = objectMapper.readTree(createBody).path("data").path("id").asLong();

            mockMvc.perform(put("/api/products/" + productId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"수정된이름\",\"price\":20000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("수정된이름"))
                    .andExpect(jsonPath("$.data.price").value(20000));
        }
    }

    // ===== DELETE /api/products/{id} — 소프트 삭제 =====

    @Nested
    @DisplayName("DELETE /api/products/{id} — 소프트 삭제")
    class Delete {

        @Test
        @DisplayName("삭제 후 status=DISCONTINUED, GET으로 여전히 조회 가능")
        void deleteProduct_softDelete() throws Exception {
            String adminToken = createAdminAndLogin("admin4", "adminpass4", "admin4@example.com");

            String responseBody = mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"삭제테스트\",\"sku\":\"SKU-DEL\",\"price\":3000}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            long productId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

            // 소프트 삭제 → 204
            mockMvc.perform(delete("/api/products/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // 삭제 후에도 GET 가능 — 상태만 DISCONTINUED
            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISCONTINUED"));
        }
    }

    // ===== GET /api/products =====

    @Nested
    @DisplayName("GET /api/products")
    class GetList {

        @Test
        @DisplayName("상품 목록 페이징 조회 → 200")
        void getProducts_list_200() throws Exception {
            String adminToken = createAdminAndLogin("admin6", "adminpass6", "admin6@example.com");

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"상품1\",\"sku\":\"SKU-L01\",\"price\":1000}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/products")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }
}
