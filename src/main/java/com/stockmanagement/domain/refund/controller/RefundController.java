package com.stockmanagement.domain.refund.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.service.RefundService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Refund", description = "환불 API")
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final UserService userService;

    @Operation(summary = "환불 요청")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefundResponse> requestRefund(
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(refundService.requestRefund(request, username));
    }

    @Operation(summary = "내 환불 목록", description = "현재 로그인 사용자의 환불 내역을 최신순으로 페이징 조회한다.")
    @GetMapping("/my")
    public ApiResponse<Page<RefundResponse>> getMyRefunds(
            @AuthenticationPrincipal String username,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(refundService.getMyRefunds(resolveUserId(authentication, username), pageable));
    }

    @Operation(summary = "환불 단건 조회", description = "ADMIN은 모든 환불 조회 가능. USER는 본인 주문의 환불만 가능.")
    @GetMapping("/{refundId}")
    public ApiResponse<RefundResponse> getById(
            @PathVariable Long refundId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(refundService.getById(refundId, username, isAdmin));
    }

    @Operation(summary = "결제 ID로 환불 조회", description = "ADMIN은 모든 환불 조회 가능. USER는 본인 주문의 환불만 가능.")
    @GetMapping("/payments/{paymentId}")
    public ApiResponse<RefundResponse> getByPaymentId(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(refundService.getByPaymentId(paymentId, username, isAdmin));
    }

    /**
     * JWT claim 또는 DB 조회로 userId를 결정한다.
     *
     * <p>JwtAuthenticationFilter가 {@code auth.setDetails(userId)}에 저장한 경우 DB 조회를 건너뛴다.
     * 구 토큰 또는 테스트 환경처럼 details가 없으면 userService.resolveUserId(username)로 DB 조회.
     */
    private Long resolveUserId(Authentication auth, String username) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userService.resolveUserId(username);
    }
}
