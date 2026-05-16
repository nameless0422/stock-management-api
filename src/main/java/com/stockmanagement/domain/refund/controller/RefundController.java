package com.stockmanagement.domain.refund.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Refund", description = "환불 API")
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "환불 요청")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefundResponse> requestRefund(
            @Valid @RequestBody RefundRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(refundService.requestRefund(request, userId));
    }

    @Operation(summary = "내 환불 목록", description = "현재 로그인 사용자의 환불 내역을 최신순으로 페이징 조회한다.")
    @GetMapping("/my")
    public ApiResponse<Page<RefundResponse>> getMyRefunds(
            @CurrentUserId Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(refundService.getMyRefunds(userId, pageable));
    }

    @Operation(summary = "환불 단건 조회", description = "ADMIN은 모든 환불 조회 가능. USER는 본인 주문의 환불만 가능.")
    @GetMapping("/{refundId}")
    public ApiResponse<RefundResponse> getById(
            @PathVariable Long refundId,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(refundService.getById(refundId, userId, isAdmin));
    }

    @Operation(summary = "결제 ID로 환불 목록 조회", description = "부분 취소 시 다건 반환. ADMIN은 모든 환불 조회 가능. USER는 본인 주문의 환불만 가능.")
    @GetMapping("/payments/{paymentId}")
    public ApiResponse<List<RefundResponse>> getByPaymentId(
            @PathVariable Long paymentId,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(refundService.getByPaymentId(paymentId, userId, isAdmin));
    }
}
