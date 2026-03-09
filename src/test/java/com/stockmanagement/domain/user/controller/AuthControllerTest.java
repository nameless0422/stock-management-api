package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.user.dto.LoginResponse;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.service.UserService;
import com.stockmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController 단위 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    // ===== POST /api/auth/signup =====

    @Nested
    @DisplayName("POST /api/auth/signup")
    class Signup {

        private static final String VALID_JSON =
                "{\"username\":\"testuser\",\"password\":\"password123\",\"email\":\"test@example.com\"}";

        @Test
        @DisplayName("회원가입 성공 (인증 불필요) → 201")
        void signupSuccess() throws Exception {
            UserResponse response = new UserResponse(1L, "testuser", "test@example.com", UserRole.USER, null);
            given(userService.signup(any())).willReturn(response);

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("필수 필드(username, password, email) 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("username 3자 미만 → 400")
        void usernameTooShort() throws Exception {
            String json = "{\"username\":\"ab\",\"password\":\"password123\",\"email\":\"test@example.com\"}";

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("중복 username → 409")
        void duplicateUsername() throws Exception {
            given(userService.signup(any()))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_USERNAME));

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== POST /api/auth/login =====

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        private static final String VALID_JSON =
                "{\"username\":\"testuser\",\"password\":\"password123\"}";

        @Test
        @DisplayName("로그인 성공 (인증 불필요) — JWT 반환 → 200")
        void loginSuccess() throws Exception {
            LoginResponse response = LoginResponse.of("jwt-token", 86400L);
            given(userService.login(any())).willReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("필수 필드(username, password) 누락 → 400")
        void validationFailure() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 자격증명 → 401")
        void invalidCredentials() throws Exception {
            given(userService.login(any()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
