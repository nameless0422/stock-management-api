package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Inventory 통합 테스트")
class InventoryIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    private long createProduct(String adminToken, String name, String sku, int price) throws Exception {
        return createProduct(adminToken, name, sku, price, null);
    }

    private long createProduct(String adminToken, String name, String sku, int price, String category) throws Exception {
        Long categoryId = null;
        if (category != null) {
            // 카테고리 목록에서 동일 이름이 있으면 재사용, 없으면 생성
            String listBody = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode cats = objectMapper.readTree(listBody).path("data");
            for (com.fasterxml.jackson.databind.JsonNode cat : cats) {
                if (category.equals(cat.path("name").asText())) {
                    categoryId = cat.path("id").asLong();
                    break;
                }
            }
            if (categoryId == null) {
                String catBody = mockMvc.perform(post("/api/categories")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("{\"name\":\"%s\"}", category)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();
            }
        }
        String catPart = categoryId != null ? ",\"categoryId\":" + categoryId : "";
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d%s}", name, sku, price, catPart)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void receive(String adminToken, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    // ===== 입고 처리 =====

    @Test
    @DisplayName("입고 처리 → onHand·available 증가")
    void receive_updatesStock() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품A", "SKU-A", 1000);

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHand").value(30))
                .andExpect(jsonPath("$.data.reserved").value(0))
                .andExpect(jsonPath("$.data.allocated").value(0))
                .andExpect(jsonPath("$.data.available").value(30));
    }

    @Test
    @DisplayName("입고 누적 → onHand 합산")
    void receive_accumulates() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품B", "SKU-B", 2000);

        receive(adminToken, productId, 10);
        receive(adminToken, productId, 20);

        mockMvc.perform(get("/api/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHand").value(30))
                .andExpect(jsonPath("$.data.available").value(30));
    }

    @Test
    @DisplayName("USER 권한으로 입고 시도 → 403")
    void receive_userRole_403() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품C", "SKU-C", 3000);
        String userToken = signupAndLogin("user", "userpass1", "user@example.com");

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 상품 입고 → 404")
    void receive_productNotFound_404() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");

        mockMvc.perform(post("/api/inventory/99999/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ===== 재고 이력 조회 =====

    @Test
    @DisplayName("입고 후 이력 조회 → RECEIVE 이력 1건, 스냅샷 검증")
    void getTransactions_afterReceive() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품D", "SKU-D", 5000);
        receive(adminToken, productId, 15);

        mockMvc.perform(get("/api/inventory/" + productId + "/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("RECEIVE"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(15))
                .andExpect(jsonPath("$.data.content[0].snapshotOnHand").value(15))
                .andExpect(jsonPath("$.data.content[0].snapshotReserved").value(0))
                .andExpect(jsonPath("$.data.content[0].snapshotAllocated").value(0));
    }

    @Test
    @DisplayName("복수 입고 → 이력 최신순 정렬")
    void getTransactions_multipleReceives_latestFirst() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품E", "SKU-E", 1000);

        receive(adminToken, productId, 10); // 첫 번째 입고
        receive(adminToken, productId, 5);  // 두 번째 입고

        mockMvc.perform(get("/api/inventory/" + productId + "/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].quantity").value(5))   // 최신
                .andExpect(jsonPath("$.data.content[0].snapshotOnHand").value(15))
                .andExpect(jsonPath("$.data.content[1].quantity").value(10)); // 이전
    }

    @Test
    @DisplayName("주문 생성 → RESERVE 이력 기록")
    void getTransactions_afterReserve() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        long productId = createProduct(adminToken, "상품F", "SKU-F", 2000);
        receive(adminToken, productId, 20);

        String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
        long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();

        // 주문 생성 → reserve() 호출
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"reserve-test-001\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":4,\"unitPrice\":2000}]}",
                                buyerId, productId)))
                .andExpect(status().isCreated());

        // 이력: RESERVE(최신), RECEIVE 순
        mockMvc.perform(get("/api/inventory/" + productId + "/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].type").value("RESERVE"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(4))
                .andExpect(jsonPath("$.data.content[0].snapshotReserved").value(4))
                .andExpect(jsonPath("$.data.content[1].type").value("RECEIVE"));
    }

    @Test
    @DisplayName("재고 레코드 없는 상품 이력 조회 → 404")
    void getTransactions_noInventory_404() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
        // 상품만 등록하고 입고하지 않음
        long productId = createProduct(adminToken, "상품G", "SKU-G", 1000);

        mockMvc.perform(get("/api/inventory/" + productId + "/transactions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("미인증 이력 조회 → 401")
    void getTransactions_unauthenticated_403() throws Exception {
        mockMvc.perform(get("/api/inventory/1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // ===== 재고 목록 검색 =====

    @Nested
    @DisplayName("GET /api/inventory — 재고 목록 검색")
    class SearchInventory {

        @Test
        @DisplayName("필터 없이 전체 조회 → 입고된 재고 수만큼 반환")
        void noFilter_returnsAll() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long p1 = createProduct(adminToken, "상품1", "SKU-S1", 1000);
            long p2 = createProduct(adminToken, "상품2", "SKU-S2", 2000);
            receive(adminToken, p1, 20);
            receive(adminToken, p2, 5);

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("status=IN_STOCK — available >= 10인 재고만 반환")
        void filterByInStock() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long inStock = createProduct(adminToken, "정상재고상품", "SKU-IN", 1000);
            long lowStock = createProduct(adminToken, "저재고상품", "SKU-LO", 2000);
            receive(adminToken, inStock, 50); // available=50 → IN_STOCK
            receive(adminToken, lowStock, 5); // available=5  → LOW_STOCK

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("status", "IN_STOCK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].available").value(50));
        }

        @Test
        @DisplayName("status=LOW_STOCK — 0 < available < 10인 재고만 반환")
        void filterByLowStock() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long inStock = createProduct(adminToken, "정상재고상품", "SKU-IN2", 1000);
            long lowStock = createProduct(adminToken, "저재고상품", "SKU-LO2", 2000);
            receive(adminToken, inStock, 30); // available=30 → IN_STOCK
            receive(adminToken, lowStock, 3); // available=3  → LOW_STOCK

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("status", "LOW_STOCK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].available").value(3));
        }

        @Test
        @DisplayName("status=OUT_OF_STOCK — 주문으로 재고 소진 후 품절 재고만 반환")
        void filterByOutOfStock() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long normalProduct = createProduct(adminToken, "정상재고상품", "SKU-NORM", 1000);
            long soldOut = createProduct(adminToken, "품절상품", "SKU-SO", 2000);
            receive(adminToken, normalProduct, 20); // available=20
            receive(adminToken, soldOut, 2);        // available=2 → 주문으로 소진 예정

            // 품절 상품 재고 전량 주문 → reserved=2, available=0
            String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
            long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();
            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"outofstock-test\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":2,\"unitPrice\":2000}]}",
                                    buyerId, soldOut)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("status", "OUT_OF_STOCK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].available").value(0));
        }

        @Test
        @DisplayName("minAvailable / maxAvailable 범위 필터 — 범위 내 재고만 반환")
        void filterByAvailableRange() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long p1 = createProduct(adminToken, "재고5", "SKU-R1", 1000);
            long p2 = createProduct(adminToken, "재고15", "SKU-R2", 1000);
            long p3 = createProduct(adminToken, "재고30", "SKU-R3", 1000);
            receive(adminToken, p1, 5);
            receive(adminToken, p2, 15);
            receive(adminToken, p3, 30);

            // available 10 이상 20 이하 → p2(15)만 해당
            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("minAvailable", "10")
                            .param("maxAvailable", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].available").value(15));
        }

        @Test
        @DisplayName("productName 키워드 검색 — 부분 일치, 대소문자 무시")
        void filterByProductName() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long laptop = createProduct(adminToken, "Samsung Laptop Pro", "SKU-LAP", 1500000);
            long phone  = createProduct(adminToken, "iPhone 15", "SKU-PHN", 1200000);
            receive(adminToken, laptop, 10);
            receive(adminToken, phone, 20);

            // "laptop" 소문자 검색 → Samsung Laptop Pro 반환
            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("productName", "laptop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].productName").value("Samsung Laptop Pro"));
        }

        @Test
        @DisplayName("category 키워드 검색 — 부분 일치")
        void filterByCategory() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long elec = createProduct(adminToken, "노트북A", "SKU-EL", 1000000, "전자기기");
            long food = createProduct(adminToken, "과자A", "SKU-FD", 3000, "식품");
            receive(adminToken, elec, 15);
            receive(adminToken, food, 100);

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("category", "전자"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].category").value("전자기기"));
        }

        @Test
        @DisplayName("status + category 복합 필터 — AND 조건으로 결합")
        void filterByStatusAndCategory() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long e1 = createProduct(adminToken, "전자A", "SKU-EA", 500000, "전자기기");
            long e2 = createProduct(adminToken, "전자B", "SKU-EB", 700000, "전자기기");
            long f1 = createProduct(adminToken, "식품A", "SKU-FA", 3000, "식품");
            receive(adminToken, e1, 50); // IN_STOCK + 전자기기
            receive(adminToken, e2, 3);  // LOW_STOCK + 전자기기
            receive(adminToken, f1, 30); // IN_STOCK + 식품

            // IN_STOCK AND 전자기기 → e1만 해당
            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("status", "IN_STOCK")
                            .param("category", "전자기기"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].productName").value("전자A"));
        }

        @Test
        @DisplayName("페이지네이션 — size=2 요청 시 2건만 반환, 페이지 메타 정확")
        void pagination() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            for (int i = 1; i <= 3; i++) {
                long pid = createProduct(adminToken, "상품" + i, "SKU-P" + i, 1000);
                receive(adminToken, pid, 10 + i);
            }

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(2));
        }

        @Test
        @DisplayName("응답에 productName, category 필드 포함")
        void responseIncludesProductInfo() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long pid = createProduct(adminToken, "테스트상품", "SKU-TP", 10000, "테스트카테고리");
            receive(adminToken, pid, 10);

            mockMvc.perform(get("/api/inventory")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].productName").value("테스트상품"))
                    .andExpect(jsonPath("$.data.content[0].category").value("테스트카테고리"))
                    .andExpect(jsonPath("$.data.content[0].available").exists());
        }

        @Test
        @DisplayName("미인증 요청 → 401")
        void unauthenticated_403() throws Exception {
            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
