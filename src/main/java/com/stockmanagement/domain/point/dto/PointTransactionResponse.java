package com.stockmanagement.domain.point.dto;

import com.stockmanagement.domain.point.entity.PointTransaction;
import com.stockmanagement.domain.point.entity.PointTransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PointTransactionResponse {

    private Long id;
    private long amount;
    private PointTransactionType type;
    private String description;
    private Long orderId;
    private LocalDateTime createdAt;

    public static PointTransactionResponse from(PointTransaction tx) {
        return PointTransactionResponse.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .type(tx.getType())
                .description(tx.getDescription())
                .orderId(tx.getOrderId())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
