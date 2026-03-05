package com.stockmanagement.domain.payment.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.payment.dto.*;
import com.stockmanagement.domain.payment.infrastructure.dto.TossWebhookEvent;
import com.stockmanagement.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Prepares a payment session.
     * Call this before rendering the TossPayments checkout widget.
     * Returns the {@code tossOrderId} and {@code amount} to pass to the widget.
     */
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> prepare(
            @RequestBody @Valid PaymentPrepareRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.prepare(request)));
    }

    /**
     * Confirms a payment with TossPayments.
     * Call this after TossPayments redirects the customer back with {@code paymentKey}.
     * On success, transitions the order to CONFIRMED and moves inventory to allocated.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @RequestBody @Valid PaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.confirm(request)));
    }

    /**
     * Cancels / refunds a payment.
     * On success, transitions the order back to CANCELLED and releases allocated inventory.
     */
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @PathVariable String paymentKey,
            @RequestBody @Valid PaymentCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancel(paymentKey, request)));
    }

    /**
     * Receives webhook events from TossPayments.
     *
     * <p>This endpoint must be publicly accessible (no authentication required).
     * TossPayments expects HTTP 2xx within 10 seconds; failures trigger up to 7 retries.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody TossWebhookEvent event) {
        paymentService.handleWebhook(event);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves payment details by TossPayments paymentKey.
     */
    @GetMapping("/{paymentKey}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByPaymentKey(
            @PathVariable String paymentKey) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByPaymentKey(paymentKey)));
    }
}
