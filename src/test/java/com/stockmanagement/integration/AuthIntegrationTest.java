package com.stockmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth 통합 테스트")
class AuthIntegrationTest extends AbstractIntegrationTest {

    // ===== POST /api/auth/signup =====

    @Nested
    @DisplayName("POST /api/auth/signup")
    class Signup {

        @Test
        @DisplayName("회원가입 성공 → 201")
        void signup_success() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"password123\",\"email\":\"new@example.com\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("newuser"))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("username 중복 → 409")
        void signup_duplicateUsername_conflict() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"dupuser\",\"password\":\"password123\",\"email\":\"first@example.com\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"dupuser\",\"password\":\"password123\",\"email\":\"second@example.com\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("email 중복 → 409")
        void signup_duplicateEmail_conflict() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"user1\",\"password\":\"password123\",\"email\":\"dup@example.com\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"user2\",\"password\":\"password123\",\"email\":\"dup@example.com\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("username 길이 미달 (2자) → 400")
        void signup_shortUsername_badRequest() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"ab\",\"password\":\"password123\",\"email\":\"ab@example.com\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일 형식 오류 → 400")
        void signup_invalidEmail_badRequest() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"validuser\",\"password\":\"password123\",\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===== POST /api/auth/login =====

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("로그인 성공 → 200, token 포함")
        void login_success() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"logintest\",\"password\":\"password123\",\"email\":\"login@example.com\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"logintest\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").isNumber());
        }

        @Test
        @DisplayName("비밀번호 불일치 → 401")
        void login_wrongPassword_unauthorized() throws Exception {
            signupAndLogin("testuser2", "correctpass", "test2@example.com");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"testuser2\",\"password\":\"wrongpass\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 → 401")
        void login_unknownUser_unauthorized() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"ghost\",\"password\":\"password123\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===== GET /api/users/me =====

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("유효한 JWT → 200, 내 정보 반환")
        void getMe_withValidToken() throws Exception {
            String token = signupAndLogin("meuser", "password123", "me@example.com");

            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("meuser"))
                    .andExpect(jsonPath("$.data.email").value("me@example.com"));
        }

        @Test
        @DisplayName("토큰 없음 → 403")
        void getMe_withoutToken_forbidden() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isForbidden());
        }
    }
}
