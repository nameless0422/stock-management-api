package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.RefreshTokenStore;
import com.stockmanagement.domain.user.dto.LoginRequest;
import com.stockmanagement.domain.user.dto.LoginResponse;
import com.stockmanagement.domain.user.dto.RefreshRequest;
import com.stockmanagement.domain.user.dto.SignupRequest;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 REST API 컨트롤러.
 *
 * <pre>
 * POST /api/auth/signup   회원가입 → 201 Created
 * POST /api/auth/login    로그인 (Access Token + Refresh Token 발급) → 200 OK
 * POST /api/auth/refresh  Access Token 재발급 (Refresh Token rotation) → 200 OK
 * POST /api/auth/logout   로그아웃 (Access Token 블랙리스트 + Refresh Token 무효화) → 200 OK
 * </pre>
 */
@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtBlacklist jwtBlacklist;
    private final RefreshTokenStore refreshTokenStore;

    @Operation(summary = "회원가입", description = "username 중복 시 409.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 5, windowSeconds = 3600, keyType = RateLimit.KeyType.IP)
    public ApiResponse<UserResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(userService.signup(request));
    }

    @Operation(summary = "로그인", description = "성공 시 accessToken(JWT) + refreshToken 반환.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @Operation(summary = "Access Token 재발급",
               description = "Refresh Token으로 새 Access Token + Refresh Token 발급(rotation). 기존 Refresh Token은 무효화.")
    @PostMapping("/refresh")
    @RateLimit(limit = 10, windowSeconds = 60, keyType = RateLimit.KeyType.IP)
    public ApiResponse<LoginResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        return ApiResponse.ok(userService.refresh(request));
    }

    @Operation(summary = "로그아웃",
               description = "Authorization 헤더의 Access Token을 블랙리스트에 등록하고 Refresh Token을 무효화한다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) RefreshRequest refreshRequest) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        jwtBlacklist.revoke(token);
        if (refreshRequest != null && refreshRequest.refreshToken() != null) {
            refreshTokenStore.revoke(refreshRequest.refreshToken());
        }
        return ApiResponse.ok(null);
    }
}
