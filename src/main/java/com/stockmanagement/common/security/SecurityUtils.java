package com.stockmanagement.common.security;

import org.springframework.security.core.Authentication;

/** Spring Security 관련 공통 유틸리티. */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 인증 정보에서 ADMIN 권한 여부를 반환한다.
     *
     * @param authentication Spring Security 인증 객체 (null 허용)
     * @return {@code ROLE_ADMIN} 권한 보유 시 {@code true}
     */
    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
