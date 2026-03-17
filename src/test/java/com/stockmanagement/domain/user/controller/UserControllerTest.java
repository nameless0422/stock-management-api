package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 단위 테스트.
 *
 * <p>UserController는 @AuthenticationPrincipal String username을 사용한다.
 * @WithMockUser는 UserDetails 객체를 principal로 주입하므로
 * SecurityMockMvcRequestPostProcessors.authentication()으로 String principal을 직접 설정한다.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtBlacklist jwtBlacklist;

    /** String principal을 가진 인증 토큰 생성 헬퍼 */
    private static UsernamePasswordAuthenticationToken userAuth(String username) {
        return new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ===== GET /api/users/me =====

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("인증된 사용자 — 내 정보 조회 → 200")
        void returnsMyInfo() throws Exception {
            UserResponse response = new UserResponse(1L, "testuser", "test@example.com", UserRole.USER, null);
            given(userService.getMe("testuser")).willReturn(response);

            mockMvc.perform(get("/api/users/me")
                            .with(authentication(userAuth("testuser"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== GET /api/users/me/orders =====

    @Nested
    @DisplayName("GET /api/users/me/orders")
    class GetMyOrders {

        @Test
        @DisplayName("인증된 사용자 — 내 주문 목록 페이징 조회 → 200")
        void returnsMyOrders() throws Exception {
            given(userService.getMyOrders(eq("testuser"), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/users/me/orders")
                            .with(authentication(userAuth("testuser"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("인증 없음 → 403")
        void unauthorizedWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/users/me/orders"))
                    .andExpect(status().isForbidden());
        }
    }
}
