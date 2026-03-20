package com.stockmanagement.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("주문 목록 필터 통합 테스트")
class OrderFilterIntegrationTest extends AbstractIntegrationTest {

    private String adminToken;
    private String userToken1;
    private String userToken2;
    private long userId1;
    private long userId2;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        userToken1 = signupAndLogin("user1", "password1", "u1@test.com");
        userToken2 = signupAndLogin("user2", "password1", "u2@test.com");
        userId1 = userRepository.findByUsername("user1").orElseThrow().getId();
        userId2 = userRepository.findByUsername("user2").orElseThrow().getId();
        productId = createProductAndReceive("SKU-F1", 1000, 100);
    }

    // ===== 공통 헬퍼 =====

    private long createProductAndReceive(String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pid = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + pid + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
        return pid;
    }

    private void createOrder(String token, long userId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":1000}]}",
                                userId, idempotencyKey, productId)))
                .andExpect(status().isCreated());
    }

    private long createOrderAndGetId(String token, long userId, String idempotencyKey) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":1000}]}",
                                userId, idempotencyKey, productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ===== USER 권한: 본인 주문만 조회 =====

    @Test
    @DisplayName("USER — 필터 없음: 본인 주문만 반환")
    void user_noFilter_returnsOnlyOwnOrders() throws Exception {
        createOrder(userToken1, userId1, "ik-user1-a");
        createOrder(userToken1, userId1, "ik-user1-b");
        createOrder(userToken2, userId2, "ik-user2-a");

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].userId").value(userId1));
    }

    @Test
    @DisplayName("USER — userId 파라미터 지정해도 본인 주문만 반환 (무시됨)")
    void user_userIdParam_ignored() throws Exception {
        createOrder(userToken1, userId1, "ik-user1-c");
        createOrder(userToken2, userId2, "ik-user2-b");

        // user1이 user2의 주문을 조회하려 해도 본인 주문만 나와야 한다
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1)
                        .param("userId", String.valueOf(userId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(userId1));
    }

    // ===== 상태 필터 =====

    @Test
    @DisplayName("USER — status=PENDING 필터: PENDING 주문만 반환")
    void filter_byStatus_pending() throws Exception {
        long orderId = createOrderAndGetId(userToken1, userId1, "ik-status-a");
        createOrder(userToken1, userId1, "ik-status-b");

        // 첫 번째 주문 취소 (→ CANCELLED)
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("USER — status=CANCELLED 필터: 취소된 주문만 반환")
    void filter_byStatus_cancelled() throws Exception {
        long orderId = createOrderAndGetId(userToken1, userId1, "ik-status-c");
        createOrder(userToken1, userId1, "ik-status-d");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1)
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELLED"));
    }

    // ===== ADMIN 권한 =====

    @Test
    @DisplayName("ADMIN — userId 파라미터: 특정 사용자 주문만 반환")
    void admin_filterByUserId() throws Exception {
        createOrder(userToken1, userId1, "ik-admin-a");
        createOrder(userToken2, userId2, "ik-admin-b");

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", String.valueOf(userId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(userId1));
    }

    @Test
    @DisplayName("ADMIN — userId 없이 전체 조회")
    void admin_noUserId_returnsAll() throws Exception {
        createOrder(userToken1, userId1, "ik-admin-c");
        createOrder(userToken2, userId2, "ik-admin-d");

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("ADMIN — userId + status 복합 필터")
    void admin_filterByUserIdAndStatus() throws Exception {
        long orderId = createOrderAndGetId(userToken1, userId1, "ik-combo-a");
        createOrder(userToken1, userId1, "ik-combo-b"); // PENDING 유지
        createOrder(userToken2, userId2, "ik-combo-c");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", String.valueOf(userId1))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    // ===== 날짜 범위 필터 =====

    @Test
    @DisplayName("startDate / endDate 범위 내 주문만 반환")
    void filter_byDateRange() throws Exception {
        createOrder(userToken1, userId1, "ik-date-a");

        String today = java.time.LocalDate.now().toString();

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1)
                        .param("startDate", today)
                        .param("endDate", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("endDate 이전으로만 지정 → 결과 없음")
    void filter_endDateBeforeToday_noResults() throws Exception {
        createOrder(userToken1, userId1, "ik-date-b");

        String yesterday = java.time.LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + userToken1)
                        .param("endDate", yesterday))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
