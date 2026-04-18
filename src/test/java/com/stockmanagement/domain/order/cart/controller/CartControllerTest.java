package com.stockmanagement.domain.order.cart.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.order.cart.dto.CartResponse;
import com.stockmanagement.domain.order.cart.service.CartService;
import com.stockmanagement.domain.order.dto.OrderResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
@DisplayName("CartController 단위 테스트")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CartService cartService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId("user1")).willReturn(1L);
    }

    // ===== GET /api/cart =====

    @Nested
    @DisplayName("GET /api/cart")
    class GetCart {

        @Test
        @DisplayName("인증된 사용자 — 장바구니 조회 → 200")
        void returnsCart() throws Exception {
            given(cartService.getCart(1L)).willReturn(mock(CartResponse.class));

            mockMvc.perform(get("/api/cart").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/cart"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== POST /api/cart/items =====

    @Nested
    @DisplayName("POST /api/cart/items")
    class AddOrUpdate {

        private static final String ITEM_JSON = "{\"productId\":1,\"quantity\":2}";

        @Test
        @DisplayName("인증된 사용자 — 상품 추가 → 200")
        void addsItem() throws Exception {
            given(cartService.addOrUpdate(anyLong(), any())).willReturn(mock(CartResponse.class));

            mockMvc.perform(post("/api/cart/items")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ITEM_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("필수 필드 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/cart/items")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== DELETE /api/cart/items/{productId} =====

    @Nested
    @DisplayName("DELETE /api/cart/items/{productId}")
    class RemoveItem {

        @Test
        @DisplayName("인증된 사용자 — 상품 제거 → 204")
        void removesItem() throws Exception {
            mockMvc.perform(delete("/api/cart/items/1").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/cart/items/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== DELETE /api/cart =====

    @Nested
    @DisplayName("DELETE /api/cart")
    class Clear {

        @Test
        @DisplayName("인증된 사용자 — 장바구니 비우기 → 204")
        void clearsCart() throws Exception {
            mockMvc.perform(delete("/api/cart").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }
    }

    // ===== POST /api/cart/checkout =====

    @Nested
    @DisplayName("POST /api/cart/checkout")
    class Checkout {

        private static final String CHECKOUT_JSON = "{\"idempotencyKey\":\"key-001\"}";

        @Test
        @DisplayName("인증된 사용자 — 주문 전환 → 201")
        void checksOut() throws Exception {
            given(cartService.checkout(anyLong(), any())).willReturn(mock(OrderResponse.class));

            mockMvc.perform(post("/api/cart/checkout")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CHECKOUT_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("멱등성 키 누락 → 400")
        void missingIdempotencyKey() throws Exception {
            mockMvc.perform(post("/api/cart/checkout")
                            .with(authentication(USER_AUTH))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
