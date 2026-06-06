package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.dto.CursorPage;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.review.dto.ReviewResponse;
import com.stockmanagement.domain.product.review.service.ReviewService;
import com.stockmanagement.domain.user.dto.ChangePasswordRequest;
import com.stockmanagement.domain.user.dto.RecentlyViewedRequest;
import com.stockmanagement.domain.user.dto.UpdateProfileRequest;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.service.RecentlyViewedService;
import com.stockmanagement.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 REST API 컨트롤러.
 *
 * <pre>
 * GET  /api/users/me              내 정보 조회 (포인트 잔액 포함) → 200 OK
 * GET  /api/users/me/orders       내 주문 목록 (페이징) → 200 OK
 * GET  /api/users/me/reviews      내 리뷰 목록 (별점 필터 + 페이징) → 200 OK
 * </pre>
 */
@Tag(name = "사용자", description = "내 정보 조회 · 내 주문·리뷰 목록 — JWT 인증 필요")
@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ReviewService reviewService;
    private final RecentlyViewedService recentlyViewedService;

    @Operation(summary = "내 정보 조회", description = "포인트 잔액(pointBalance) 포함.")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal String username) {
        return ApiResponse.ok(userService.getMe(username));
    }

    @Operation(summary = "회원 탈퇴", description = "논리 삭제 처리. 탈퇴 후 동일 계정으로 로그인 불가.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deactivate(
            @AuthenticationPrincipal String username,
            @RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        userService.deactivate(username, accessToken);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "프로필 수정", description = "이메일 변경. 중복 이메일이면 409.")
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(username, request));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경. 기존 Access/Refresh Token 일괄 무효화.")
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal String username,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ChangePasswordRequest request) {
        String accessToken = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        userService.changePassword(username, request, accessToken);
    }

    @Operation(summary = "내 주문 목록 (커서 기반)", description = "기본: 최신순, 20건.")
    @GetMapping("/me/orders")
    public ApiResponse<CursorPage<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.getMyOrders(username, lastId, size));
    }

    @Operation(summary = "내 리뷰 목록 (커서 기반)",
               description = "별점 필터 지원 (?rating=1~5).")
    @GetMapping("/me/reviews")
    public ApiResponse<CursorPage<ReviewResponse>> getMyReviews(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long lastId,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.getMyReviews(userId, lastId, size, rating));
    }

    @Operation(summary = "최근 본 상품 기록", description = "상품 조회 시 자동 호출. Redis ZSet에 저장.")
    @PostMapping("/me/recently-viewed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordRecentlyViewed(
            @CurrentUserId Long userId,
            @Valid @RequestBody RecentlyViewedRequest request) {
        recentlyViewedService.record(userId, request.getProductId());
    }

    @Operation(summary = "최근 본 상품 목록", description = "최신순 반환. 기본 10개, 최대 50개.")
    @GetMapping("/me/recently-viewed")
    public ApiResponse<List<ProductResponse>> getRecentlyViewed(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(recentlyViewedService.getRecentlyViewed(userId, size));
    }
}
