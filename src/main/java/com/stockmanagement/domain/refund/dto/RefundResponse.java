package com.stockmanagement.domain.refund.dto;

import com.stockmanagement.domain.refund.entity.Refund;
import com.stockmanagement.domain.refund.entity.RefundStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RefundResponse {

    private Long id;
    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static RefundResponse from(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPaymentId())
                .orderId(refund.getOrderId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .createdAt(refund.getCreatedAt())
                .completedAt(refund.getCompletedAt())
                .build();
    }
}
