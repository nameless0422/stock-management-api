package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 캐시(@Cacheable / @CacheEvict) 통합 테스트.
 *
 * <p>Redis Testcontainer를 통해 실제 캐시 동작을 검증한다:
 * <ul>
 *   <li>캐시 직렬화/역직렬화 정상 동작 (cache hit 시 DTO 복원)
 *   <li>write 후 캐시 evict — 다음 읽기에 최신 데이터 반환
 * </ul>
 */
@DisplayName("Cache 통합 테스트")
class CacheIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    private long createProduct(String adminToken, String name, String sku) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"sku\":\"%s\",\"price\":10000}", name, sku)))
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

    // ===== Inventory 캐시 =====

    @Nested
    @DisplayName("inventory 캐시")
    class InventoryCache {

        @Test
        @DisplayName("캐시 hit — 두 번 조회해도 동일한 데이터 반환 (역직렬화 정상 동작)")
        void cacheHit_returnsSameData() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long productId = createProduct(adminToken, "캐시상품", "CACHE-SKU-1");
            receive(adminToken, productId, 20);

            // 첫 번째 조회 — cache miss (DB → Redis 저장)
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.onHand").value(20))
                    .andExpect(jsonPath("$.data.available").value(20));

            // 두 번째 조회 — cache hit (Redis에서 역직렬화)
            // 동일한 값이 반환되면 직렬화/역직렬화가 정상 동작하는 것
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.onHand").value(20))
                    .andExpect(jsonPath("$.data.available").value(20));
        }

        @Test
        @DisplayName("캐시 evict — 입고 후 최신 재고 반환")
        void cacheEvict_afterReceive_returnsFreshData() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long productId = createProduct(adminToken, "입고캐시상품", "CACHE-SKU-2");
            receive(adminToken, productId, 10);

            // 첫 조회 — cache miss: onHand=10 캐싱
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(jsonPath("$.data.onHand").value(10));

            // 추가 입고 → @CacheEvict 발동
            receive(adminToken, productId, 15);

            // 재조회 — cache miss (evict됨): 최신 데이터(25) 반환
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.onHand").value(25))
                    .andExpect(jsonPath("$.data.available").value(25));
        }

        @Test
        @DisplayName("캐시 evict — 주문 생성(reserve) 후 최신 재고 반환")
        void cacheEvict_afterReserve_returnsFreshData() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long productId = createProduct(adminToken, "주문캐시상품", "CACHE-SKU-3");
            receive(adminToken, productId, 30);

            // 첫 조회 — available=30 캐싱
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(jsonPath("$.data.available").value(30));

            // 주문 생성 → reserve() → @CacheEvict 발동
            String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
            long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();
            mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"cache-test-001\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":5,\"unitPrice\":10000}]}",
                                    buyerId, productId)))
                    .andExpect(status().isCreated());

            // 재조회 — cache miss: reserved=5, available=25
            mockMvc.perform(get("/api/inventory/" + productId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reserved").value(5))
                    .andExpect(jsonPath("$.data.available").value(25));
        }
    }

    // ===== Order 캐시 =====

    @Nested
    @DisplayName("orders 캐시")
    class OrderCache {

        @Test
        @DisplayName("캐시 hit — 두 번 조회해도 동일한 주문 반환 (역직렬화 정상 동작)")
        void cacheHit_returnsSameOrder() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long productId = createProduct(adminToken, "주문용상품", "CACHE-ORD-1");
            receive(adminToken, productId, 20);

            String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
            long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();

            // 주문 생성
            String orderBody = mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"cache-ord-001\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":2,\"unitPrice\":10000}]}",
                                    buyerId, productId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

            // 첫 번째 조회 — cache miss
            mockMvc.perform(get("/api/orders/" + orderId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.items.length()").value(1));

            // 두 번째 조회 — cache hit (역직렬화 정상 동작 확인)
            mockMvc.perform(get("/api/orders/" + orderId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.items.length()").value(1));
        }

        @Test
        @DisplayName("캐시 evict — 주문 취소 후 최신 상태(CANCELLED) 반환")
        void cacheEvict_afterCancel_returnsCancelledStatus() throws Exception {
            String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@example.com");
            long productId = createProduct(adminToken, "취소캐시상품", "CACHE-ORD-2");
            receive(adminToken, productId, 20);

            String userToken = signupAndLogin("buyer", "buyerpass1", "buyer@example.com");
            long buyerId = userRepository.findByUsername("buyer").orElseThrow().getId();

            // 주문 생성
            String orderBody = mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userId\":%d,\"idempotencyKey\":\"cache-ord-002\"," +
                                    "\"items\":[{\"productId\":%d,\"quantity\":3,\"unitPrice\":10000}]}",
                                    buyerId, productId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long orderId = objectMapper.readTree(orderBody).path("data").path("id").asLong();

            // 첫 조회 — PENDING 캐싱
            mockMvc.perform(get("/api/orders/" + orderId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            // 주문 취소 → @CacheEvict 발동
            mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk());

            // 재조회 — cache miss: 최신 상태(CANCELLED) 반환
            mockMvc.perform(get("/api/orders/" + orderId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }
    }
}
