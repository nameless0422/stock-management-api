package com.stockmanagement.domain.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.infrastructure.TossWebhookVerifier;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.service.PaymentService;
import com.stockmanagement.domain.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stockmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment operations.
 *
 * <p>Endpoint overview:
 * <pre>
 *   POST /api/payments/prepare             – prepare payment session (before checkout widget)
 *   POST /api/payments/confirm             – confirm payment (after checkout widget)
 *   POST /api/payments/{paymentKey}/cancel – cancel / refund payment
 *   POST /api/payments/webhook             – receive TossPayments webhook events (public)
 *   GET  /api/payments/{paymentKey}        – query payment details by paymentKey
 *   GET  /api/payments/order/{orderId}     – query payment details by orderId (admin use)
 * </pre>
 */
@Tag(name = "결제", description = "TossPayments 결제 준비 · 승인 · 취소 · 조회 · 웹훅")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TossWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Operation(summary = "결제 준비", description = "TossPayments 결제창 렌더링 전 호출. tossOrderId와 amount 반환. 본인 주문만 가능.")
    @PostMapping("/prepare")
    public ApiResponse<PaymentPrepareResponse> prepare(
            @RequestBody @Valid PaymentPrepareRequest request,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        return ApiResponse.ok(paymentService.prepare(request, resolveUserId(authentication, username)));
    }

    @Operation(summary = "결제 승인", description = "결제창 완료 후 paymentKey로 호출. Order → CONFIRMED, reserved→allocated. 본인 주문만 가능.")
    @PostMapping("/confirm")
    @RateLimit(limit = 5, windowSeconds = 60)
    public ApiResponse<PaymentResponse> confirm(
            @RequestBody @Valid PaymentConfirmRequest request,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        return ApiResponse.ok(paymentService.confirm(request, resolveUserId(authentication, username)));
    }

    @Operation(summary = "결제 취소/환불", description = "본인 결제만 취소 가능. ADMIN은 전체 취소 가능. Order → CANCELLED, allocated 해제.")
    @PostMapping("/{paymentKey}/cancel")
    public ApiResponse<PaymentResponse> cancel(
            @PathVariable String paymentKey,
            @RequestBody @Valid PaymentCancelRequest request,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.cancel(paymentKey, request, resolveUserId(authentication, username), isAdmin));
    }

    @Operation(summary = "TossPayments 웹훅 수신", description = "공개 엔드포인트. Toss-Signature 헤더로 HMAC-SHA256 서명 검증 후 처리.")
    @PostMapping("/webhook")
    @ResponseStatus(org.springframework.http.HttpStatus.OK)
    public void webhook(
            @RequestHeader(value = "Toss-Signature", required = false) String signature,
            @RequestBody String rawBody) throws JsonProcessingException {
        webhookVerifier.verify(rawBody, signature);
        TossWebhookEvent event = objectMapper.readValue(rawBody, TossWebhookEvent.class);
        paymentService.handleWebhook(event);
    }

    @Operation(summary = "결제 조회", description = "paymentKey로 결제 상세 조회. 본인 결제만 가능 (ADMIN은 전체 조회).")
    @GetMapping("/{paymentKey}")
    public ApiResponse<PaymentResponse> getByPaymentKey(
            @PathVariable String paymentKey,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.getByPaymentKey(paymentKey, resolveUserId(authentication, username), isAdmin));
    }

    @Operation(summary = "주문별 결제 조회", description = "주문 ID로 결제 정보 조회. 결제 전이면 data: null 반환. 본인 주문만 가능.")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(
            @PathVariable Long orderId,
            @AuthenticationPrincipal String username,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.getByOrderId(orderId, resolveUserId(authentication, username), isAdmin).orElse(null));
    }

    /**
     * JWT claim 또는 DB 조회로 userId를 결정한다.
     *
     * <p>JwtAuthenticationFilter가 {@code auth.setDetails(userId)}에 저장한 경우 DB 조회를 건너뛴다.
     * 구 토큰 또는 테스트 환경처럼 details가 없으면 userService.resolveUserId(username)으로 DB 조회.
     */
    private Long resolveUserId(Authentication auth, String username) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return userService.resolveUserId(username);
    }
}
