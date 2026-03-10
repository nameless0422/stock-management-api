package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
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
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%d}", name, sku, price)))
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
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("RECEIVE"))
                .andExpect(jsonPath("$.data[0].quantity").value(15))
                .andExpect(jsonPath("$.data[0].snapshotOnHand").value(15))
                .andExpect(jsonPath("$.data[0].snapshotReserved").value(0))
                .andExpect(jsonPath("$.data[0].snapshotAllocated").value(0));
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
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].quantity").value(5))   // 최신
                .andExpect(jsonPath("$.data[0].snapshotOnHand").value(15))
                .andExpect(jsonPath("$.data[1].quantity").value(10)); // 이전
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
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("RESERVE"))
                .andExpect(jsonPath("$.data[0].quantity").value(4))
                .andExpect(jsonPath("$.data[0].snapshotReserved").value(4))
                .andExpect(jsonPath("$.data[1].type").value("RECEIVE"));
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
    @DisplayName("미인증 이력 조회 → 403")
    void getTransactions_unauthenticated_403() throws Exception {
        mockMvc.perform(get("/api/inventory/1/transactions"))
                .andExpect(status().isForbidden());
    }
}
