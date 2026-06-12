package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Product Q&A 통합 테스트")
class ProductQnaIntegrationTest extends AbstractIntegrationTest {

    // ===== 공통 헬퍼 =====

    private long createProduct(String adminToken, String sku) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"상품_%s\",\"sku\":\"%s\",\"price\":10000}", sku, sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createQna(String userToken, long productId, String content, boolean secret) throws Exception {
        String json = String.format("{\"content\":\"%s\",\"secret\":%s}", content, secret);
        String body = mockMvc.perform(post("/api/v1/products/" + productId + "/qna")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("질문 작성 → 201, 내용 확인")
    void createQna_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-1");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        mockMvc.perform(post("/api/v1/products/" + productId + "/qna")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"배송 기간이 얼마나 걸리나요?\",\"secret\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("배송 기간이 얼마나 걸리나요?"))
                .andExpect(jsonPath("$.data.secret").value(false))
                .andExpect(jsonPath("$.data.answered").value(false));
    }

    @Test
    @DisplayName("비밀글 작성 → 타인 조회 시 마스킹")
    void secretQna_maskedForOthers() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-2");
        String user1Token = signupAndLogin("user1", "Password1!", "user1@test.com");
        String user2Token = signupAndLogin("user2", "Password1!", "user2@test.com");

        createQna(user1Token, productId, "비밀 문의입니다", true);

        // user2가 조회 — 마스킹
        mockMvc.perform(get("/api/v1/products/" + productId + "/qna")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("비밀글입니다"))
                .andExpect(jsonPath("$.data.content[0].secret").value(true));
    }

    @Test
    @DisplayName("비밀글 작성 → 본인 조회 시 원문 노출")
    void secretQna_visibleToAuthor() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-3");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        createQna(userToken, productId, "비밀 문의입니다", true);

        // 작성자 본인 조회 — 원문
        mockMvc.perform(get("/api/v1/products/" + productId + "/qna")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("비밀 문의입니다"));
    }

    @Test
    @DisplayName("ADMIN 답변 작성 → answer 필드 확인")
    void adminAnswer_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-4");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        long qnaId = createQna(userToken, productId, "사이즈 문의", false);

        mockMvc.perform(post("/api/v1/products/" + productId + "/qna/" + qnaId + "/answer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"M 사이즈를 추천드립니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered").value(true))
                .andExpect(jsonPath("$.data.answer").value("M 사이즈를 추천드립니다."));
    }

    @Test
    @DisplayName("작성자 삭제 → 204")
    void deleteByAuthor_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-5");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        long qnaId = createQna(userToken, productId, "삭제할 문의", false);

        mockMvc.perform(delete("/api/v1/products/" + productId + "/qna/" + qnaId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // 삭제 후 조회 — 빈 목록
        mockMvc.perform(get("/api/v1/products/" + productId + "/qna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("타인 삭제 시도 → 403")
    void deleteByOther_forbidden() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-6");
        String user1Token = signupAndLogin("user1", "Password1!", "user1@test.com");
        String user2Token = signupAndLogin("user2", "Password1!", "user2@test.com");

        long qnaId = createQna(user1Token, productId, "문의", false);

        mockMvc.perform(delete("/api/v1/products/" + productId + "/qna/" + qnaId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 삭제 → 204")
    void deleteByAdmin_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-7");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        long qnaId = createQna(userToken, productId, "문의", false);

        mockMvc.perform(delete("/api/v1/products/" + productId + "/qna/" + qnaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("비로그인 목록 조회 → 200")
    void getListWithoutAuth_success() throws Exception {
        String adminToken = createAdminAndLogin("admin", "Password1!", "admin@test.com");
        long productId = createProduct(adminToken, "QNA-8");
        String userToken = signupAndLogin("user1", "Password1!", "user1@test.com");

        createQna(userToken, productId, "공개 문의", false);

        mockMvc.perform(get("/api/v1/products/" + productId + "/qna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("공개 문의"));
    }
}
