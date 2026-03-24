package com.stockmanagement.integration;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Review 통합 테스트")
class ReviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;

    // ===== 공통 헬퍼 =====

    /** 상품 생성 + 재고 입고 후 productId 반환. */
    private long createProductAndReceive(String adminToken, String sku, int price, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":%d}", sku, sku, price)))
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

    /**
     * 주문 생성 후 상태를 CONFIRMED로 직접 변경한다.
     * TossPayments API 없이 구매 완료 상태를 시뮬레이션한다.
     */
    private void placeConfirmedOrder(String userToken, long userId, long productId, int price) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":%d,\"idempotencyKey\":\"%s\"," +
                                "\"items\":[{\"productId\":%d,\"quantity\":1,\"unitPrice\":%d}]}",
                                userId, UUID.randomUUID(), productId, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(body).path("data").path("id").asLong();

        Order order = orderRepository.findById(orderId).orElseThrow();
        order.confirm();
        orderRepository.save(order);
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("실구매자 리뷰 작성 → 201, 평점·제목 확인")
    void createReview_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV1", 10000, 10);

        String userToken = signupAndLogin("reviewer", "password1", "rev@test.com");
        long userId = userRepository.findByUsername("reviewer").orElseThrow().getId();
        placeConfirmedOrder(userToken, userId, productId, 10000);

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"title\":\"좋아요\",\"content\":\"매우 만족합니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.title").value("좋아요"));
    }

    @Test
    @DisplayName("미구매자 리뷰 작성 → 400 REVIEW_NOT_PURCHASED")
    void createReview_notPurchased() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV2", 10000, 10);

        // 구매 이력 없는 사용자
        String userToken = signupAndLogin("stranger", "password1", "str@test.com");

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"title\":\"그냥\",\"content\":\"특별함 없음\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 리뷰 작성 → 409 REVIEW_ALREADY_EXISTS")
    void createReview_duplicate() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV3", 10000, 10);

        String userToken = signupAndLogin("reviewer2", "password1", "rev2@test.com");
        long userId = userRepository.findByUsername("reviewer2").orElseThrow().getId();
        placeConfirmedOrder(userToken, userId, productId, 10000);

        String reviewJson = "{\"rating\":4,\"title\":\"보통\",\"content\":\"나쁘지 않음\"}";
        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson))
                .andExpect(status().isCreated());

        // 두 번째 리뷰 → 409
        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("리뷰 목록 조회 → 비로그인 허용, 작성된 리뷰 반환")
    void getReviews_public() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV4", 10000, 10);

        String userToken = signupAndLogin("reviewer3", "password1", "rev3@test.com");
        long userId = userRepository.findByUsername("reviewer3").orElseThrow().getId();
        placeConfirmedOrder(userToken, userId, productId, 10000);

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"title\":\"최고\",\"content\":\"강추합니다\"}"))
                .andExpect(status().isCreated());

        // 비로그인으로 목록 조회
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[0].title").value("최고"));
    }

    @Test
    @DisplayName("본인 리뷰 삭제 → 204, 이후 목록 비어 있음")
    void deleteReview_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV5", 10000, 10);

        String userToken = signupAndLogin("reviewer4", "password1", "rev4@test.com");
        long userId = userRepository.findByUsername("reviewer4").orElseThrow().getId();
        placeConfirmedOrder(userToken, userId, productId, 10000);

        String body = mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"title\":\"보통\",\"content\":\"그냥 그래요\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reviewId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/products/" + productId + "/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // 삭제 후 목록 비어 있음 확인
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("타인 리뷰 삭제 시도 → 403")
    void deleteReview_otherUser() throws Exception {
        String adminToken = createAdminAndLogin("admin", "adminpass1", "admin@test.com");
        long productId = createProductAndReceive(adminToken, "SKU-REV6", 10000, 10);

        String writerToken = signupAndLogin("writer", "password1", "wr@test.com");
        long writerId = userRepository.findByUsername("writer").orElseThrow().getId();
        placeConfirmedOrder(writerToken, writerId, productId, 10000);

        String body = mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2,\"title\":\"별로\",\"content\":\"실망\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reviewId = objectMapper.readTree(body).path("data").path("id").asLong();

        // 타인이 삭제 시도 → 403
        String otherToken = signupAndLogin("other", "password1", "ot@test.com");
        mockMvc.perform(delete("/api/products/" + productId + "/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
