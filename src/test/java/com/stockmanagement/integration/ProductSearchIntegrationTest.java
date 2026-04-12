package com.stockmanagement.integration;

import com.stockmanagement.domain.product.document.ProductDocument;
import com.stockmanagement.domain.product.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 상품 Elasticsearch 검색 통합 테스트.
 *
 * <p>상품 CRUD API를 통해 ES 색인을 검증하고,
 * 검색 파라미터(q, minPrice, maxPrice, category, sort)별 결과를 확인한다.
 *
 * <p>ES는 기본적으로 1초 주기로 인덱스를 refresh한다.
 * 테스트에서는 색인 후 {@link ElasticsearchOperations#indexOps}로 즉시 refresh를 강제한다.
 */
@DisplayName("상품 Elasticsearch 검색 통합 테스트")
class ProductSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ElasticsearchOperations elasticsearchOperations;
    @Autowired private ProductSearchRepository productSearchRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
    }

    // ===== 공통 헬퍼 =====

    /**
     * 카테고리를 조회하여 없으면 생성하고 ID를 반환한다.
     * 같은 테스트 내에서 동일 카테고리를 여러 번 사용해도 안전하다.
     */
    private long getOrCreateCategory(String name) throws Exception {
        String listBody = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode categories = objectMapper.readTree(listBody).path("data");
        for (com.fasterxml.jackson.databind.JsonNode cat : categories) {
            if (name.equals(cat.path("name").asText())) {
                return cat.path("id").asLong();
            }
        }
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\"}", name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 상품을 등록하고 ES 인덱스를 즉시 refresh한다. */
    private long createProduct(String name, String sku, int price, String category) throws Exception {
        Long categoryId = category != null ? getOrCreateCategory(category) : null;
        String productJson = categoryId != null
                ? String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d,\"categoryId\":%d}",
                        name, sku, price, categoryId)
                : String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d}", name, sku, price);

        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("data").path("id").asLong();

        // ES 인덱스 즉시 refresh (기본 1초 delay 제거)
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();
        return id;
    }

    // ===== 검색 조건 없음 → MySQL 조회 =====

    @Test
    @DisplayName("검색 조건 없음 → MySQL로 ACTIVE 상품 목록 반환")
    void noFilter_returnsMysqlResult() throws Exception {
        createProduct("노트북", "NB-001", 1200000, "전자");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].name").value("노트북"));
    }

    // ===== 키워드 검색 =====

    @Test
    @DisplayName("q=노트북 → name 일치 상품 반환")
    void search_byKeyword_name() throws Exception {
        createProduct("노트북 Pro", "NB-002", 1500000, "전자");
        createProduct("마우스", "MS-001", 30000, "전자");

        mockMvc.perform(get("/api/products").param("q", "노트북"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("노트북 Pro"));
    }

    @Test
    @DisplayName("q=키보드 → name 일치 상품 반환 (multi_match)")
    void search_byKeyword_category() throws Exception {
        createProduct("키보드", "KB-001", 80000, "전자");
        createProduct("바지", "PT-001", 50000, "의류");

        mockMvc.perform(get("/api/products").param("q", "키보드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("KB-001"));
    }

    // ===== 가격 범위 필터 =====

    @Test
    @DisplayName("minPrice=50000&maxPrice=100000 → 범위 내 상품만 반환")
    void search_byPriceRange() throws Exception {
        createProduct("이어폰", "EP-001", 79000, "전자");
        createProduct("스마트폰", "SP-001", 1200000, "전자");
        createProduct("USB 허브", "UH-001", 25000, "전자");

        mockMvc.perform(get("/api/products")
                        .param("minPrice", "50000")
                        .param("maxPrice", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("EP-001"));
    }

    @Test
    @DisplayName("minPrice만 지정 → 해당 가격 이상 상품 반환")
    void search_byMinPrice() throws Exception {
        createProduct("태블릿", "TB-001", 700000, "전자");
        createProduct("충전기", "CG-001", 20000, "전자");

        mockMvc.perform(get("/api/products").param("minPrice", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("TB-001"));
    }

    // ===== 카테고리 필터 =====

    @Test
    @DisplayName("category=의류 → 의류 상품만 반환")
    void search_byCategory() throws Exception {
        createProduct("티셔츠", "TS-001", 29000, "의류");
        createProduct("모니터", "MN-001", 350000, "전자");

        mockMvc.perform(get("/api/products").param("category", "의류"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("TS-001"));
    }

    // ===== 복합 필터 =====

    @Test
    @DisplayName("q=USB + maxPrice=100000 복합 필터")
    void search_combined_keywordAndPrice() throws Exception {
        createProduct("USB 케이블", "UC-001", 8000, "전자");
        createProduct("외장 SSD", "SSD-001", 150000, "전자");
        createProduct("운동화", "SH-001", 80000, "패션");

        mockMvc.perform(get("/api/products")
                        .param("q", "USB")
                        .param("maxPrice", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("UC-001"));
    }

    // ===== 정렬 =====

    @Test
    @DisplayName("sort=price_asc → 가격 오름차순")
    void search_sortByPriceAsc() throws Exception {
        createProduct("고가 상품", "HP-001", 500000, "전자");
        createProduct("저가 상품", "LP-001", 10000, "전자");

        mockMvc.perform(get("/api/products")
                        .param("category", "전자")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("LP-001"))
                .andExpect(jsonPath("$.data.content[1].sku").value("HP-001"));
    }

    @Test
    @DisplayName("sort=price_desc → 가격 내림차순")
    void search_sortByPriceDesc() throws Exception {
        createProduct("중가 상품", "MP-001", 100000, "가전");
        createProduct("저가 상품", "LP-002", 5000, "가전");

        mockMvc.perform(get("/api/products")
                        .param("category", "가전")
                        .param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("MP-001"))
                .andExpect(jsonPath("$.data.content[1].sku").value("LP-002"));
    }

    // ===== 색인 동기화 =====

    @Test
    @DisplayName("상품 삭제(soft delete) → ES 색인에서 제거되어 검색 결과에 미포함")
    void delete_removesFromIndex() throws Exception {
        long id = createProduct("삭제될 상품", "DEL-001", 99000, "테스트");

        mockMvc.perform(delete("/api/products/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();

        mockMvc.perform(get("/api/products").param("q", "삭제될 상품"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("상품 수정 → ES 색인 갱신되어 새 이름으로 검색 가능")
    void update_refreshesIndex() throws Exception {
        long id = createProduct("원래 이름", "UP-001", 50000, "잡화");

        mockMvc.perform(put("/api/products/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경된 이름\",\"price\":50000,\"sku\":\"UP-001\"}"))
                .andExpect(status().isOk());
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();

        mockMvc.perform(get("/api/products").param("q", "변경된 이름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
