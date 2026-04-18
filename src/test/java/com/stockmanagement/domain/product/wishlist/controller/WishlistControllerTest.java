package com.stockmanagement.domain.product.wishlist.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.service.WishlistService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WishlistController.class)
@Import(SecurityConfig.class)
@DisplayName("WishlistController 단위 테스트")
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private WishlistService wishlistService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId(anyString())).willReturn(1L);
    }

    // ===== POST /api/wishlist/{productId} =====

    @Nested
    @DisplayName("POST /api/wishlist/{productId}")
    class Add {

        @Test
        @DisplayName("인증된 사용자 — 위시리스트 추가 → 201")
        void addsToWishlist() throws Exception {
            given(wishlistService.add(anyLong(), anyLong())).willReturn(mock(WishlistResponse.class));

            mockMvc.perform(post("/api/wishlist/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/wishlist/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("이미 추가된 상품 → 409")
        void alreadyExists() throws Exception {
            given(wishlistService.add(anyLong(), anyLong()))
                    .willThrow(new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS));

            mockMvc.perform(post("/api/wishlist/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== DELETE /api/wishlist/{productId} =====

    @Nested
    @DisplayName("DELETE /api/wishlist/{productId}")
    class Remove {

        @Test
        @DisplayName("인증된 사용자 — 위시리스트 제거 → 204")
        void removesFromWishlist() throws Exception {
            mockMvc.perform(delete("/api/wishlist/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/wishlist/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== GET /api/wishlist =====

    @Nested
    @DisplayName("GET /api/wishlist")
    class GetList {

        @Test
        @DisplayName("인증된 사용자 — 위시리스트 조회 → 200")
        void returnsList() throws Exception {
            given(wishlistService.getList(anyLong()))
                    .willReturn(List.of(mock(WishlistResponse.class)));

            mockMvc.perform(get("/api/wishlist").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/wishlist"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
