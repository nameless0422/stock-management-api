package com.stockmanagement.domain.point.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.domain.point.dto.PointBalanceResponse;
import com.stockmanagement.domain.point.dto.PointTransactionResponse;
import com.stockmanagement.domain.point.service.PointService;
import com.stockmanagement.common.security.JwtTokenProvider;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
@Import(SecurityConfig.class)
@DisplayName("PointController 단위 테스트")
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PointService pointService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private UserService userService;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    // ===== GET /api/points/balance =====

    @Nested
    @DisplayName("GET /api/points/balance")
    class GetBalance {

        @Test
        @DisplayName("인증된 사용자 — 잔액 조회 → 200")
        void returnsBalance() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            given(pointService.getBalance(any())).willReturn(mock(PointBalanceResponse.class));

            mockMvc.perform(get("/api/v1/points/balance").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/points/balance"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== GET /api/points/history =====

    @Nested
    @DisplayName("GET /api/points/history")
    class GetHistory {

        @Test
        @DisplayName("인증된 사용자 — 이력 조회 → 200")
        void returnsHistory() throws Exception {
            given(userService.resolveUserId(any())).willReturn(1L);
            given(pointService.getHistory(any(), any(), anyInt()))
                    .willReturn(CursorPage.of(List.of(mock(PointTransactionResponse.class)), 20, PointTransactionResponse::getId));

            mockMvc.perform(get("/api/v1/points/history").with(authentication(USER_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/points/history"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
