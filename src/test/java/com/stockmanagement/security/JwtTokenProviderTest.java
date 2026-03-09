package com.stockmanagement.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    // HMAC-SHA256은 최소 256 bit(32 byte) 이상의 시크릿 키가 필요
    private static final String SECRET =
            "test-secret-key-must-be-at-least-32-characters-long!!";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKeyStr", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "tokenValidityInSeconds", 3600L);
        jwtTokenProvider.init();
    }

    // ===== createToken() =====

    @Nested
    @DisplayName("createToken()")
    class CreateToken {

        @Test
        @DisplayName("username과 role을 담은 JWT 토큰을 생성한다")
        void createsNonNullToken() {
            String token = jwtTokenProvider.createToken("testuser", "USER");

            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("서로 다른 사용자에 대해 각기 다른 토큰을 생성한다")
        void createsDifferentTokensForDifferentUsers() {
            String token1 = jwtTokenProvider.createToken("user1", "USER");
            String token2 = jwtTokenProvider.createToken("user2", "USER");

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ===== getUsername() =====

    @Nested
    @DisplayName("getUsername()")
    class GetUsername {

        @Test
        @DisplayName("토큰에서 username을 추출한다")
        void extractsUsernameFromToken() {
            String token = jwtTokenProvider.createToken("testuser", "USER");

            assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("testuser");
        }
    }

    // ===== getRole() =====

    @Nested
    @DisplayName("getRole()")
    class GetRole {

        @Test
        @DisplayName("토큰에서 role을 추출한다")
        void extractsRoleFromToken() {
            String token = jwtTokenProvider.createToken("testuser", "ADMIN");

            assertThat(jwtTokenProvider.getRole(token)).isEqualTo("ADMIN");
        }
    }

    // ===== validateToken() =====

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰은 true를 반환한다")
        void returnsTrueForValidToken() {
            String token = jwtTokenProvider.createToken("testuser", "USER");

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("변조된 토큰은 false를 반환한다")
        void returnsFalseForTamperedToken() {
            String token = jwtTokenProvider.createToken("testuser", "USER");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";

            assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 false를 반환한다")
        void returnsFalseForExpiredToken() {
            JwtTokenProvider expiredProvider = new JwtTokenProvider();
            ReflectionTestUtils.setField(expiredProvider, "secretKeyStr", SECRET);
            ReflectionTestUtils.setField(expiredProvider, "tokenValidityInSeconds", -1L);
            expiredProvider.init();

            String expiredToken = expiredProvider.createToken("testuser", "USER");

            assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열은 false를 반환한다")
        void returnsFalseForEmptyString() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("형식이 잘못된 토큰은 false를 반환한다")
        void returnsFalseForMalformedToken() {
            assertThat(jwtTokenProvider.validateToken("not.a.valid.jwt")).isFalse();
        }
    }
}
