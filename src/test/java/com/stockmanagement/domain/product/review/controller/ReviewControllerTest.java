package com.stockmanagement.domain.product.review.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.service.ReviewService;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
@DisplayName("ReviewController 단위 테스트")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ReviewService reviewService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private static final String REVIEW_JSON = "{\"rating\":5,\"title\":\"좋아요\",\"content\":\"매우 만족합니다.\"}";

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId(anyString())).willReturn(1L);
    }

    // ===== POST /api/products/{id}/reviews =====

    @Nested
    @DisplayName("POST /api/products/{productId}/reviews")
    class Create {

        @Test
        @DisplayName("인증된 사용자 — 리뷰 작성 → 201")
        void createsReview() throws Exception {
            given(reviewService.create(anyLong(), anyLong(), any())).willReturn(mock(ReviewResponse.class));

            mockMvc.perform(post("/api/products/1/reviews")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REVIEW_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REVIEW_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미구매 상품 리뷰 → 400")
        void notPurchased() throws Exception {
            given(reviewService.create(anyLong(), anyLong(), any()))
                    .willThrow(new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED));

            mockMvc.perform(post("/api/products/1/reviews")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REVIEW_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("중복 리뷰 → 409")
        void duplicateReview() throws Exception {
            given(reviewService.create(anyLong(), anyLong(), any()))
                    .willThrow(new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS));

            mockMvc.perform(post("/api/products/1/reviews")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REVIEW_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/products/{id}/reviews =====

    @Nested
    @DisplayName("GET /api/products/{productId}/reviews")
    class GetList {

        @Test
        @DisplayName("비로그인 사용자 — 리뷰 목록 조회 → 200")
        void publicGetsList() throws Exception {
            given(reviewService.getList(anyLong(), any(Pageable.class), any()))
                    .willReturn(new PageImpl<>(List.of(mock(ReviewResponse.class))));

            mockMvc.perform(get("/api/products/1/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== DELETE /api/products/{id}/reviews/{reviewId} =====

    @Nested
    @DisplayName("DELETE /api/products/{productId}/reviews/{reviewId}")
    class Delete {

        @Test
        @DisplayName("인증된 사용자 — 리뷰 삭제 → 204")
        void deletesReview() throws Exception {
            mockMvc.perform(delete("/api/products/1/reviews/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/products/1/reviews/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
