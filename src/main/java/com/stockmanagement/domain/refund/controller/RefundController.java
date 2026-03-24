package com.stockmanagement.domain.refund.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.refund.dto.RefundRequest;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(refundService.requestRefund(request, username));
    }

    @Operation(summary = "환불 단건 조회")
    @GetMapping("/{refundId}")
    public ApiResponse<RefundResponse> getById(
            @PathVariable Long refundId,
            @AuthenticationPrincipal String username) {
        return ApiResponse.ok(refundService.getById(refundId, username));
    }

    @Operation(summary = "결제 ID로 환불 조회")
    @GetMapping("/payments/{paymentId}")
    public ApiResponse<RefundResponse> getByPaymentId(@PathVariable Long paymentId) {
        return ApiResponse.ok(refundService.getByPaymentId(paymentId));
    }
}
