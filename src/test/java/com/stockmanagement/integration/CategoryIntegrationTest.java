package com.stockmanagement.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Category 통합 테스트")
class CategoryIntegrationTest extends AbstractIntegrationTest {

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
    }

    // ===== 공통 헬퍼 =====

    /** 카테고리를 생성하고 ID를 반환한다. */
    private long createCategory(String name) throws Exception {
        return createCategory(name, null);
    }

    private long createCategory(String name, Long parentId) throws Exception {
        String json = parentId != null
                ? String.format("{\"name\":\"%s\",\"parentId\":%d}", name, parentId)
                : String.format("{\"name\":\"%s\"}", name);

        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 상품을 생성하고 ID를 반환한다. */
    private long createProduct(String sku, long categoryId) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":10000,\"categoryId\":%d}",
                                sku, sku, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ===== 생성 =====

    @Test
    @DisplayName("최상위 카테고리 생성 → 201, parentId=null")
    void createRootCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"전자\",\"description\":\"전자제품\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("전자"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist());
    }

    @Test
    @DisplayName("하위 카테고리 생성 → parentId·parentName 포함")
    void createChildCategory() throws Exception {
        long parentId = createCategory("전자");

        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"노트북\",\"parentId\":%d}", parentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        objectMapper.readTree(body).path("data").path("parentId").asLong();
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("이름 중복 생성 → 409 CATEGORY_DUPLICATE")
    void createDuplicate_409() throws Exception {
        createCategory("전자");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"전자\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 parentId → 404")
    void createWithInvalidParent_404() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"노트북\",\"parentId\":99999}"))
                .andExpect(status().isNotFound());
    }

    // ===== 조회 =====

    @Test
    @DisplayName("트리 조회 — 2단계 구조 확인")
    void getTree_twoLevels() throws Exception {
        long parentId = createCategory("전자");
        createCategory("노트북", parentId);
        createCategory("스마트폰", parentId);

        mockMvc.perform(get("/api/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("전자"))
                .andExpect(jsonPath("$.data[0].children.length()").value(2));
    }

    @Test
    @DisplayName("카테고리 단건 조회 → 200, children 포함")
    void getById() throws Exception {
        long parentId = createCategory("전자");
        createCategory("노트북", parentId);

        mockMvc.perform(get("/api/categories/" + parentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("전자"))
                .andExpect(jsonPath("$.data.children.length()").value(1));
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("하위 카테고리 있는 카테고리 삭제 → 409")
    void deleteWithChildren_409() throws Exception {
        long parentId = createCategory("전자");
        createCategory("노트북", parentId);

        mockMvc.perform(delete("/api/categories/" + parentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("상품이 등록된 카테고리 삭제 → 409")
    void deleteWithProducts_409() throws Exception {
        long categoryId = createCategory("전자");
        createProduct("SKU-C01", categoryId);

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("조건 충족 시 카테고리 삭제 → 204")
    void deleteCategory_204() throws Exception {
        long categoryId = createCategory("삭제대상");

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // ===== 상품 연동 =====

    @Test
    @DisplayName("categoryId로 상품 생성 → 응답에 category(name)·categoryId 포함")
    void createProductWithCategory() throws Exception {
        long categoryId = createCategory("가전");

        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"세탁기\",\"sku\":\"WM-001\",\"price\":500000,\"categoryId\":%d}",
                                categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/products/" + objectMapper.readTree(body).path("data").path("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("가전"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId));
    }

    @Test
    @DisplayName("없는 categoryId로 상품 생성 → 404")
    void createProductWithInvalidCategory_404() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"상품\",\"sku\":\"INV-001\",\"price\":10000,\"categoryId\":99999}"))
                .andExpect(status().isNotFound());
    }
}
