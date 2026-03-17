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
import com.stockmanagement.common.security.LoginRateLimiter;
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
 * <p>회원가입, 로그인(JWT 발급), 내 정보 조회, 내 주문 목록 조회를 담당한다.
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

    /** 로그인. Rate Limit 확인 → 자격증명 검증 → JWT 발급. */
    public LoginResponse login(LoginRequest request) {
        loginRateLimiter.checkAndIncrement(request.username());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginRateLimiter.reset(request.username());
        String token = jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());
        return LoginResponse.of(token, jwtTokenProvider.getTokenValidityInSeconds());
    }

    /** 현재 인증된 사용자 정보 조회. */
    public UserResponse getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /** 현재 인증된 사용자의 주문 목록 페이징 조회. */
    public Page<OrderResponse> getMyOrders(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(OrderResponse::from);
    }
}
