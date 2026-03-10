package com.stockmanagement.domain.payment.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment operations.
 *
 * <p>Endpoint overview:
 * <pre>
 *   POST /api/payments/prepare          – prepare payment session (before checkout widget)
 *   POST /api/payments/confirm          – confirm payment (after checkout widget)
 *   POST /api/payments/{paymentKey}/cancel – cancel / refund payment
 *   POST /api/payments/webhook          – receive TossPayments webhook events (public)
 *   GET  /api/payments/{paymentKey}     – query payment details
 * </pre>
 */
@Tag(name = "결제", description = "TossPayments 결제 준비 · 승인 · 취소 · 조회 · 웹훅")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 준비", description = "TossPayments 결제창 렌더링 전 호출. tossOrderId와 amount 반환.")
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> prepare(
            @RequestBody @Valid PaymentPrepareRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.prepare(request)));
    }

    @Operation(summary = "결제 승인", description = "결제창 완료 후 paymentKey로 호출. Order → CONFIRMED, reserved→allocated.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @RequestBody @Valid PaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.confirm(request)));
    }

    @Operation(summary = "결제 취소/환불", description = "Order → CANCELLED, allocated 해제.")
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @PathVariable String paymentKey,
            @RequestBody @Valid PaymentCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancel(paymentKey, request)));
    }

    @Operation(summary = "TossPayments 웹훅 수신", description = "공개 엔드포인트. TossPayments가 결제 이벤트를 POST로 전송.")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody TossWebhookEvent event) {
        paymentService.handleWebhook(event);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "결제 조회", description = "paymentKey로 결제 상세 조회.")
    @GetMapping("/{paymentKey}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByPaymentKey(
            @PathVariable String paymentKey) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByPaymentKey(paymentKey)));
    }
}
