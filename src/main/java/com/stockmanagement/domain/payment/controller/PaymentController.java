package com.stockmanagement.domain.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.stockmanagement.common.annotation.RequireEmailVerified;
import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.common.ratelimit.RateLimit;
import com.stockmanagement.common.security.CurrentUserId;
import com.stockmanagement.common.security.SecurityUtils;
import com.stockmanagement.domain.payment.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import com.stockmanagement.domain.payment.infrastructure.TossWebhookVerifier;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TossWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @Operation(summary = "결제 준비", description = "TossPayments 결제창 렌더링 전 호출. tossOrderId와 amount 반환. 본인 주문만 가능.")
    @PostMapping("/prepare")
    @RequireEmailVerified
    public ApiResponse<PaymentPrepareResponse> prepare(
            @RequestBody @Valid PaymentPrepareRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(paymentService.prepare(request, userId));
    }

    @Operation(summary = "결제 승인", description = "결제창 완료 후 paymentKey로 호출. Order → CONFIRMED, reserved→allocated. 본인 주문만 가능.")
    @PostMapping("/confirm")
    @RateLimit(limit = 5, windowSeconds = 60)
    @RequireEmailVerified
    public ApiResponse<PaymentResponse> confirm(
            @RequestBody @Valid PaymentConfirmRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.ok(paymentService.confirm(request, userId));
    }

    @Operation(summary = "결제 취소/환불", description = "본인 결제만 취소 가능. ADMIN은 전체 취소 가능. Order → CANCELLED, allocated 해제.")
    @PostMapping("/{paymentKey}/cancel")
    public ApiResponse<PaymentResponse> cancel(
            @PathVariable String paymentKey,
            @RequestBody @Valid PaymentCancelRequest request,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.cancel(paymentKey, request, userId, isAdmin));
    }

    @Operation(summary = "TossPayments 웹훅 수신", description = "공개 엔드포인트. Toss-Signature 헤더로 HMAC-SHA256 서명 검증 후 처리.")
    @PostMapping("/webhook")
    @ResponseStatus(org.springframework.http.HttpStatus.OK)
    public void webhook(
            @RequestHeader(value = "Toss-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        webhookVerifier.verify(rawBody, signature);
        try {
            TossWebhookEvent event = objectMapper.readValue(rawBody, TossWebhookEvent.class);
            paymentService.handleWebhook(event);
        } catch (JsonProcessingException e) {
            // malformed payload — 200 반환하여 Toss의 불필요한 재시도 방지
            log.warn("[Webhook] JSON 파싱 실패 — payload 무시: {}", e.getOriginalMessage());
        }
    }

    @Operation(summary = "내 결제 목록", description = "현재 로그인 사용자의 결제 내역을 최신순으로 페이징 조회한다.")
    @GetMapping("/my")
    public ApiResponse<Page<PaymentResponse>> getMyPayments(
            @CurrentUserId Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(paymentService.getMyPayments(userId, pageable));
    }

    @Operation(summary = "결제 조회", description = "paymentKey로 결제 상세 조회. 본인 결제만 가능 (ADMIN은 전체 조회).")
    @GetMapping("/{paymentKey}")
    public ApiResponse<PaymentResponse> getByPaymentKey(
            @PathVariable String paymentKey,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.getByPaymentKey(paymentKey, userId, isAdmin));
    }

    @Operation(summary = "주문별 결제 조회", description = "주문 ID로 결제 정보 조회. 결제 전이면 data: null 반환. 본인 주문만 가능.")
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(
            @PathVariable Long orderId,
            @CurrentUserId Long userId,
            Authentication authentication) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ApiResponse.ok(paymentService.getByOrderId(orderId, userId, isAdmin).orElse(null));
    }
}
