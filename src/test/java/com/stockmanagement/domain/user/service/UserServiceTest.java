package com.stockmanagement.domain.user.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.user.dto.LoginRequest;
import com.stockmanagement.domain.user.dto.LoginResponse;
import com.stockmanagement.domain.user.dto.SignupRequest;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import com.stockmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;

    /** 공통 픽스처 — 저장 완료 상태를 가정한 User */
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("testuser")
                .password("encoded-pw")
                .email("test@example.com")
                .role(UserRole.USER)
                .build();
    }

    // ===== signup() =====

    @Nested
    @DisplayName("signup()")
    class Signup {

        @Test
        @DisplayName("정상 가입 — 비밀번호 인코딩 후 User 저장")
        void savesUserWithEncodedPassword() {
            SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com");

            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("test@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("encoded-pw");
            given(userRepository.save(any(User.class))).willReturn(user);

            UserResponse response = userService.signup(request);

            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(any(User.class));
            assertThat(response.username()).isEqualTo("testuser");
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.role()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("username 중복 시 DUPLICATE_USERNAME 예외 발생, 저장 미수행")
        void throwsWhenUsernameDuplicated() {
            SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com");

            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_USERNAME));

            verify(userRepository, never()).save(any());
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("email 중복 시 DUPLICATE_EMAIL 예외 발생, 저장 미수행")
        void throwsWhenEmailDuplicated() {
            SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com");

            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_EMAIL));

            verify(userRepository, never()).save(any());
            verifyNoInteractions(passwordEncoder);
        }
    }

    // ===== login() =====

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("정상 로그인 — JWT 토큰과 만료 시간 반환")
        void returnsJwtTokenOnSuccess() {
            LoginRequest request = new LoginRequest("testuser", "password123");

            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123", "encoded-pw")).willReturn(true);
            given(jwtTokenProvider.createToken("testuser", "USER")).willReturn("jwt-token");
            given(jwtTokenProvider.getTokenValidityInSeconds()).willReturn(86400L);

            LoginResponse response = userService.login(request);

            assertThat(response.accessToken()).isEqualTo("jwt-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(86400L);
        }

        @Test
        @DisplayName("존재하지 않는 username으로 로그인 시 INVALID_CREDENTIALS 예외 발생")
        void throwsWhenUsernameNotFound() {
            LoginRequest request = new LoginRequest("unknown", "password123");

            given(userRepository.findByUsername("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 INVALID_CREDENTIALS 예외 발생")
        void throwsWhenPasswordMismatch() {
            LoginRequest request = new LoginRequest("testuser", "wrong-password");

            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong-password", "encoded-pw")).willReturn(false);

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

            verifyNoInteractions(jwtTokenProvider);
        }
    }

    // ===== getMe() =====

    @Nested
    @DisplayName("getMe()")
    class GetMe {

        @Test
        @DisplayName("인증된 사용자 정보를 반환한다")
        void returnsUserResponse() {
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

            UserResponse response = userService.getMe("testuser");

            assertThat(response.username()).isEqualTo("testuser");
            assertThat(response.email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND 예외 발생")
        void throwsWhenUserNotFound() {
            given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMe("ghost"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    // ===== getMyOrders() =====

    @Nested
    @DisplayName("getMyOrders()")
    class GetMyOrders {

        @Test
        @DisplayName("인증된 사용자의 주문 목록을 페이징하여 반환한다")
        void returnsPagedOrders() {
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            // user.getId()는 미영속 상태라 null이므로 any() 매처 사용
            given(orderRepository.findByUserId(any(), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<OrderResponse> result = userService.getMyOrders("testuser", pageable);

            assertThat(result).isEmpty();
            verify(orderRepository).findByUserId(any(), eq(pageable));
        }

        @Test
        @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND 예외 발생")
        void throwsWhenUserNotFound() {
            given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyOrders("ghost", PageRequest.of(0, 10)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            verifyNoInteractions(orderRepository);
        }
    }
}
