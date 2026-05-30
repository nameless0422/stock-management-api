package com.stockmanagement.domain.product.notification.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.product.notification.dto.RestockNotificationResponse;
import com.stockmanagement.domain.product.notification.service.RestockNotificationService;
import com.stockmanagement.domain.user.service.UserService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RestockNotificationController.class)
@Import(SecurityConfig.class)
@DisplayName("RestockNotificationController 단위 테스트")
class RestockNotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RestockNotificationService restockNotificationService;
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

    @Nested
    @DisplayName("POST /api/products/{productId}/restock-notify")
    class Subscribe {

        @Test
        @DisplayName("인증된 사용자 → 201")
        void subscribes() throws Exception {
            RestockNotificationResponse response = RestockNotificationResponse.builder()
                    .id(1L).productId(1L).productName("상품A")
                    .price(BigDecimal.valueOf(15000))
                    .createdAt(LocalDateTime.now()).build();
            given(restockNotificationService.subscribe(anyLong(), anyLong())).willReturn(response);

            mockMvc.perform(post("/api/v1/products/1/restock-notify").with(authentication(USER_AUTH)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.productId").value(1));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/products/1/restock-notify"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("이미 구독 중 → 409")
        void alreadySubscribed() throws Exception {
            given(restockNotificationService.subscribe(anyLong(), anyLong()))
                    .willThrow(new BusinessException(ErrorCode.RESTOCK_ALREADY_SUBSCRIBED));

            mockMvc.perform(post("/api/v1/products/1/restock-notify").with(authentication(USER_AUTH)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{productId}/restock-notify")
    class Unsubscribe {

        @Test
        @DisplayName("인증된 사용자 → 204")
        void unsubscribes() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1/restock-notify").with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1/restock-notify"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/users/me/restock-notifications")
    class GetMyNotifications {

        @Test
        @DisplayName("인증된 사용자 → 200, 목록 반환")
        void returnsNotifications() throws Exception {
            RestockNotificationResponse r = RestockNotificationResponse.builder()
                    .id(1L).productId(1L).productName("상품A")
                    .price(BigDecimal.valueOf(15000))
                    .createdAt(LocalDateTime.now()).build();
            given(restockNotificationService.getMyNotifications(anyLong())).willReturn(List.of(r));

            mockMvc.perform(get("/api/v1/users/me/restock-notifications").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].productId").value(1));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/restock-notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
