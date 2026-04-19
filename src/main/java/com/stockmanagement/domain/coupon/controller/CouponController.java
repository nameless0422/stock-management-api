package com.stockmanagement.domain.coupon.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.coupon.dto.CouponClaimRequest;
import com.stockmanagement.domain.coupon.dto.CouponCreateRequest;
import com.stockmanagement.domain.coupon.dto.CouponIssueRequest;
import com.stockmanagement.domain.coupon.dto.CouponResponse;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.dto.MyCouponResponse;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final UserService userService;

    @Operation(summary = "쿠폰 생성 [ADMIN]")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.ok(couponService.create(request));
    }

    @Operation(summary = "쿠폰 목록 [ADMIN]")
    @GetMapping
    public ApiResponse<Page<CouponResponse>> getList(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(couponService.getList(pageable));
    }

    @Operation(summary = "쿠폰 상세 [ADMIN]")
    @GetMapping("/{id}")
    public ApiResponse<CouponResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(couponService.getById(id));
    }

    @Operation(summary = "쿠폰 비활성화 [ADMIN]")
    @PatchMapping("/{id}/deactivate")
    public ApiResponse<CouponResponse> deactivate(@PathVariable Long id) {
        return ApiResponse.ok(couponService.deactivate(id));
    }

    @Operation(summary = "쿠폰 발급 [ADMIN] — 특정 사용자에게 쿠폰 지급")
    @PostMapping("/{id}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> issue(@PathVariable Long id,
                                   @Valid @RequestBody CouponIssueRequest request) {
        couponService.issueToUser(id, request.getUserId());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "내 쿠폰 목록 [USER] — 발급된 쿠폰 + 사용 가능 여부",
               description = "usable=true: 사용 가능한 쿠폰만, usable=false: 만료/소진된 쿠폰만, 미지정: 전체.")
    @GetMapping("/my")
    public ApiResponse<List<MyCouponResponse>> getMyCoupons(
            @AuthenticationPrincipal String username,
            @RequestParam(required = false) Boolean usable) {
        Long userId = userService.resolveUserId(username);
        return ApiResponse.ok(couponService.getMyCoupons(userId, usable));
    }

    @Operation(summary = "공개 쿠폰 등록 [USER] — 쿠폰 코드 입력으로 내 지갑에 추가")
    @PostMapping("/claim")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MyCouponResponse> claim(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CouponClaimRequest request) {
        Long userId = userService.resolveUserId(username);
        return ApiResponse.ok(couponService.claim(userId, request));
    }

    @Operation(summary = "쿠폰 유효성 확인 + 할인 금액 미리보기 [USER]")
    @PostMapping("/validate")
    public ApiResponse<CouponValidateResponse> validate(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CouponValidateRequest request) {
        Long userId = userService.resolveUserId(username);
        return ApiResponse.ok(couponService.validate(userId, request));
    }
}
