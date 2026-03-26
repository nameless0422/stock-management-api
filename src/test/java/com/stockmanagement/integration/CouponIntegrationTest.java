package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Coupon 통합 테스트")
class CouponIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    /** ADMIN 쿠폰 생성 후 쿠폰 ID 반환. */
    private long createCoupon(String adminToken, String code, String type,
                              int discountValue, int maxUsage) throws Exception {
        String json = String.format(
                "{\"code\":\"%s\",\"name\":\"테스트 쿠폰\"," +
                "\"discountType\":\"%s\",\"discountValue\":%d," +
                "\"maxUsageCount\":%d,\"maxUsagePerUser\":1," +
                "\"validFrom\":\"2020-01-01T00:00:00\",\"validUntil\":\"2099-12-31T23:59:59\"}",
                code, type, discountValue, maxUsage);

        String body = mockMvc.perform(post("/api/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 상품 생성 + 재고 입고 후 productId 반환. */
    private long createProductAndReceive(String adminToken, String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}",
                                sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/inventory/" + productId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
        return productId;
    }

    /** 주문 생성 후 orderId 반환. userId는 실제 DB에서 조회한 값을 사용해야 한다. */
    private long createOrder(String userToken, long userId, long productId, int price,
                             String couponCode) throws Exception {
        String couponPart = couponCode != null ? ",\"couponCode\":\"" + couponCode + "\"" : "";
        String json = String.format(
                "{\"userId\":%d,\"idempotencyKey\":\"%s\"%s," +
                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":%d}]}",
                userId, UUID.randomUUID(), couponPart, productId, price);

        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("쿠폰 생성 → 주문에 쿠폰 적용 → discountAmount 확인")
    void applyCouponOnOrder() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP1", 30000, 10);
        createCoupon(adminToken, "FIXED5K", "FIXED_AMOUNT", 5000, 100);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"couponCode\":\"FIXED5K\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":30000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.discountAmount").value(5000))
                .andReturn().getResponse().getContentAsString();

        // couponId 필드 존재 확인
        long couponId = objectMapper.readTree(body).path("data").path("couponId").asLong();
        assert couponId > 0 : "couponId must be set";
    }

    @Test
    @DisplayName("PERCENTAGE 쿠폰 적용 — 캡 적용 확인")
    void percentageCouponWithCap() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP2", 50000, 10);

        // 10% 할인, 캡 3000
        String json = "{\"code\":\"PCT10\",\"name\":\"10% 할인\"," +
                      "\"discountType\":\"PERCENTAGE\",\"discountValue\":10," +
                      "\"maxDiscountAmount\":3000," +
                      "\"maxUsageCount\":100,\"maxUsagePerUser\":1," +
                      "\"validFrom\":\"2020-01-01T00:00:00\",\"validUntil\":\"2099-12-31T23:59:59\"}";
        mockMvc.perform(post("/api/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        // 50000 × 10% = 5000 → 캡 3000 적용
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"couponCode\":\"PCT10\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":50000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.discountAmount").value(3000));
    }

    @Test
    @DisplayName("주문 생성 후 취소 → 쿠폰 usageCount 복원 확인")
    void cancelOrderRestoresCouponUsage() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP3", 20000, 10);
        long couponId  = createCoupon(adminToken, "CANCEL1", "FIXED_AMOUNT", 2000, 5);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        long orderId = createOrder(userToken, userId, productId, 20000, "CANCEL1");

        // 쿠폰 usageCount=1 확인
        mockMvc.perform(get("/api/coupons/" + couponId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageCount").value(1));

        // 주문 취소
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // usageCount 복원 확인
        mockMvc.perform(get("/api/coupons/" + couponId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageCount").value(0));
    }

    @Test
    @DisplayName("소진 쿠폰 (maxUsageCount=1) — 2번째 사용 → 409")
    void exhaustedCoupon() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP4", 10000, 10);
        createCoupon(adminToken, "ONETIME", "FIXED_AMOUNT", 1000, 1);

        // 첫 번째 사용자 — 성공
        String user1Token = signupAndLogin("buyer1", "password1", "b1@test.com");
        long user1Id = userRepository.findByUsername("buyer1").orElseThrow().getId();
        createOrder(user1Token, user1Id, productId, 10000, "ONETIME");

        // 두 번째 사용자 — COUPON_EXHAUSTED (409)
        String user2Token = signupAndLogin("buyer2", "password1", "b2@test.com");
        long user2Id = userRepository.findByUsername("buyer2").orElseThrow().getId();
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"couponCode\":\"ONETIME\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                user2Id, UUID.randomUUID(), productId)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("같은 사용자가 동일 쿠폰 재사용 → 409")
    void sameUserCouponReuse() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP5", 10000, 10);
        createCoupon(adminToken, "ONCE_PER_USER", "FIXED_AMOUNT", 1000, 100);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        // 첫 번째 사용 — 성공
        createOrder(userToken, userId, productId, 10000, "ONCE_PER_USER");

        // 두 번째 사용 — COUPON_ALREADY_USED (409)
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"couponCode\":\"ONCE_PER_USER\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("쿠폰 유효성 확인 API — 할인 금액 미리보기")
    void validateCoupon() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        createCoupon(adminToken, "PREVIEW", "FIXED_AMOUNT", 3000, 100);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");

        mockMvc.perform(post("/api/coupons/validate")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponCode\":\"PREVIEW\",\"orderAmount\":20000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount").value(3000))
                .andExpect(jsonPath("$.data.finalAmount").value(17000));
    }

    @Test
    @DisplayName("ADMIN — 쿠폰 비활성화 → 비활성화 쿠폰 사용 시 409")
    void deactivateCoupon() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP6", 10000, 10);
        long couponId  = createCoupon(adminToken, "DEACT", "FIXED_AMOUNT", 1000, 100);

        // 비활성화
        mockMvc.perform(patch("/api/coupons/" + couponId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // 비활성화된 쿠폰 사용 → 409 COUPON_INACTIVE
        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\",\"couponCode\":\"DEACT\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":10000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isConflict());
    }

    // ===== 쿠폰 발급 / 내 쿠폰 목록 =====

    @Test
    @DisplayName("ADMIN 쿠폰 발급 → GET /api/coupons/my에서 조회됨")
    void issueCouponAndGetMyCoupons() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long couponId = createCoupon(adminToken, "MY-COUPON", "FIXED_AMOUNT", 3000, 100);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        // 발급
        mockMvc.perform(post("/api/coupons/" + couponId + "/issue")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated());

        // 내 쿠폰 목록 조회 — 발급된 쿠폰 1건, isUsable=true
        mockMvc.perform(get("/api/coupons/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("MY-COUPON"))
                .andExpect(jsonPath("$.data[0].isUsable").value(true));
    }

    @Test
    @DisplayName("동일 쿠폰 중복 발급 → 409 COUPON_ALREADY_ISSUED")
    void duplicateIssuance() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long couponId = createCoupon(adminToken, "ONCE-ISSUE", "FIXED_AMOUNT", 1000, 100);
        long userId = userRepository.findByUsername("admin").orElseThrow().getId();

        String body = "{\"userId\":" + userId + "}";

        // 첫 번째 발급 — 성공
        mockMvc.perform(post("/api/coupons/" + couponId + "/issue")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // 두 번째 발급 — 409
        mockMvc.perform(post("/api/coupons/" + couponId + "/issue")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("쿠폰 사용 후 isUsable = false")
    void usedCouponNotUsable() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-MY1", 20000, 5);
        long couponId = createCoupon(adminToken, "USE-ONCE", "FIXED_AMOUNT", 1000, 100);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        // 발급
        mockMvc.perform(post("/api/coupons/" + couponId + "/issue")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated());

        // 쿠폰 사용해서 주문
        createOrder(userToken, userId, productId, 20000, "USE-ONCE");

        // 내 쿠폰 — isUsable = false (maxUsagePerUser=1 이미 사용)
        mockMvc.perform(get("/api/coupons/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isUsable").value(false));
    }

    @Test
    @DisplayName("발급받지 않은 사용자 — 내 쿠폰 목록 빈 배열")
    void noIssuedCoupons() throws Exception {
        String userToken = signupAndLogin("buyer", "password1", "b@test.com");

        mockMvc.perform(get("/api/coupons/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("쿠폰 없는 일반 주문 — discountAmount=0")
    void orderWithoutCoupon() throws Exception {
        String adminToken = createAdminAndLogin("admin", "password1", "a@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-COUP7", 15000, 10);

        String userToken = signupAndLogin("buyer", "password1", "b@test.com");
        long userId = userRepository.findByUsername("buyer").orElseThrow().getId();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":15000}]}",
                                userId, UUID.randomUUID(), productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.discountAmount").value(0));
    }
}
