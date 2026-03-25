package com.stockmanagement.domain.user.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.user.dto.LoginRequest;
import com.stockmanagement.domain.user.dto.LoginResponse;
import com.stockmanagement.domain.user.dto.RefreshRequest;
import com.stockmanagement.domain.user.dto.SignupRequest;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import com.stockmanagement.common.security.LoginRateLimiter;
import com.stockmanagement.common.security.RefreshTokenStore;
import com.stockmanagement.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 비즈니스 로직 서비스.
 *
 * <p>회원가입, 로그인(JWT + Refresh Token 발급), 토큰 재발급, 내 정보 조회, 내 주문 목록 조회를 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenStore refreshTokenStore;

    /** 회원가입. username/email 중복 시 예외. */
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .role(UserRole.USER)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    /** 로그인. Rate Limit 확인 → 자격증명 검증 → Access Token + Refresh Token 발급. */
    public LoginResponse login(LoginRequest request) {
        loginRateLimiter.checkAndIncrement(request.username());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginRateLimiter.reset(request.username());
        String accessToken  = jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());
        String refreshToken = refreshTokenStore.issue(user.getUsername());
        return LoginResponse.of(accessToken, jwtTokenProvider.getTokenValidityInSeconds(), refreshToken);
    }

    /**
     * Refresh Token으로 새 Access Token + Refresh Token 발급 (rotation).
     *
     * @throws BusinessException 토큰이 유효하지 않거나 만료된 경우
     */
    public LoginResponse refresh(RefreshRequest request) {
        String username = refreshTokenStore.consume(request.refreshToken());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken  = jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());
        String newRefreshToken = refreshTokenStore.issue(user.getUsername());
        return LoginResponse.of(newAccessToken, jwtTokenProvider.getTokenValidityInSeconds(), newRefreshToken);
    }

    /** username으로 사용자 ID를 반환한다. */
    public Long resolveUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }

    /** 현재 인증된 사용자 정보 조회. */
    public UserResponse getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /** 회원 탈퇴 — 논리 삭제 처리. username/email을 익명화하여 동일 정보로 재가입 가능. */
    @Transactional
    public void deactivate(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // unique 슬롯 해방: 탈퇴 계정이 email/username UNIQUE KEY를 점유하지 않도록 익명화
        user.anonymize(user.getId());

        userRepository.delete(user); // @SQLDelete → UPDATE users SET deleted_at = NOW(6) WHERE id = ?

        // 탈퇴 시 해당 사용자의 모든 Refresh Token 즉시 폐기 (보안)
        // Access Token은 JWT 만료 시까지 유효하나, 재발급 경로인 Refresh Token을 차단한다
        refreshTokenStore.revokeAll(username);
    }

    /** 현재 인증된 사용자의 주문 목록 페이징 조회. */
    public Page<OrderResponse> getMyOrders(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(OrderResponse::from);
    }
}
