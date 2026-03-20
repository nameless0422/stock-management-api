package com.stockmanagement.domain.coupon.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.coupon.dto.CouponCreateRequest;
import com.stockmanagement.domain.coupon.dto.CouponResponse;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.service.CouponService;
import com.stockmanagement.domain.user.repository.UserRepository;
import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final UserRepository userRepository;

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

    @Operation(summary = "쿠폰 유효성 확인 + 할인 금액 미리보기 [USER]")
    @PostMapping("/validate")
    public ApiResponse<CouponValidateResponse> validate(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CouponValidateRequest request) {
        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getId();
        return ApiResponse.ok(couponService.validate(userId, request));
    }
}
