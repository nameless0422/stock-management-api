package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.user.dto.LoginRequest;
import com.stockmanagement.domain.user.dto.LoginResponse;
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
 * POST /api/auth/signup  회원가입 → 201 Created
 * POST /api/auth/login   로그인 (JWT 발급) → 200 OK
 * POST /api/auth/logout  로그아웃 (JWT 블랙리스트 등록) → 200 OK
 * </pre>
 */
@Tag(name = "인증", description = "회원가입 · 로그인 (JWT 발급) · 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtBlacklist jwtBlacklist;

    @Operation(summary = "회원가입", description = "username 중복 시 409.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(userService.signup(request));
    }

    @Operation(summary = "로그인", description = "성공 시 accessToken(JWT) 반환.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @Operation(summary = "로그아웃", description = "Authorization 헤더의 JWT를 블랙리스트에 등록. 이후 해당 토큰은 사용 불가.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        jwtBlacklist.revoke(token);
        return ApiResponse.ok(null);
    }
}
