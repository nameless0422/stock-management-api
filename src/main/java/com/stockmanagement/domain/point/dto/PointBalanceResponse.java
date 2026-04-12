package com.stockmanagement.domain.point.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PointBalanceResponse {

    private Long userId;
    private long balance;

    public static PointBalanceResponse of(Long userId, long balance) {
        return PointBalanceResponse.builder()
                .userId(userId)
                .balance(balance)
                .build();
    }
}
