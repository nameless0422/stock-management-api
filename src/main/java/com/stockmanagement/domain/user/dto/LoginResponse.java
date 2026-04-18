package com.stockmanagement.domain.user.dto;

import java.time.LocalDateTime;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        LocalDateTime refreshTokenExpiresAt
) {
    public static LoginResponse of(String accessToken, long expiresInSeconds, String refreshToken) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, refreshToken,
                LocalDateTime.now().plusDays(30));
    }
}
