package com.stockmanagement.domain.user.dto;

import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        LocalDateTime createdAt,
        /** 포인트 잔액 — null이면 포인트 레코드 미생성 (첫 적립 전) */
        Long pointBalance
) {
    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, Long pointBalance) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                pointBalance
        );
    }
}
