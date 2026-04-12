package com.stockmanagement.domain.user.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {
    public static LoginResponse of(String accessToken, long expiresInSeconds, String refreshToken) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, refreshToken);
    }
}
