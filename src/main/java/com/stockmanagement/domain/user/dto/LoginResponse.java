package com.stockmanagement.domain.user.dto;

import java.time.LocalDateTime;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        LocalDateTime refreshTokenExpiresAt,
        boolean emailVerified
) {
    public static LoginResponse of(String accessToken, long expiresInSeconds, String refreshToken,
                                   boolean emailVerified) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, refreshToken,
                LocalDateTime.now().plusDays(30), emailVerified);
    }

    /** 하위 호환 — emailVerified 미지정 시 true 기본값 */
    public static LoginResponse of(String accessToken, long expiresInSeconds, String refreshToken) {
        return of(accessToken, expiresInSeconds, refreshToken, true);
    }
}
