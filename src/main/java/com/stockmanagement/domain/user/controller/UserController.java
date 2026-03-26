package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.user.dto.ChangePasswordRequest;
import com.stockmanagement.domain.user.dto.UpdateProfileRequest;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 REST API 컨트롤러.
 *
 * <pre>
 * GET /api/users/me          내 정보 조회 → 200 OK
 * GET /api/users/me/orders   내 주문 목록 (페이징) → 200 OK
 * </pre>
 *
 * JWT 인증이 필요하다. {@link AuthenticationPrincipal}로 username을 주입받아 서비스에 전달한다.
 */
@Tag(name = "사용자", description = "내 정보 조회 · 내 주문 목록 — JWT 인증 필요")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal String username) {
        return ApiResponse.ok(userService.getMe(username));
    }

    @Operation(summary = "회원 탈퇴", description = "논리 삭제 처리. 탈퇴 후 동일 계정으로 로그인 불가.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal String username) {
        userService.deactivate(username);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "프로필 수정", description = "이메일 변경. 중복 이메일이면 409.")
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(username, request));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경.")
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(username, request);
    }

    @Operation(summary = "내 주문 목록 (페이징)", description = "기본: 최신순, 20건.")
    @GetMapping("/me/orders")
    public ApiResponse<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal String username,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(userService.getMyOrders(username, pageable));
    }
}
