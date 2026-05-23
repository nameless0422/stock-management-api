package com.stockmanagement.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequest(
        @NotBlank(message = "인증 토큰은 필수입니다.")
        String token
) {
}
