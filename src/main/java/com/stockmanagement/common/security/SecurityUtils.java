package com.stockmanagement.common.security;

import org.springframework.security.core.Authentication;

import java.util.function.Supplier;

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

    /**
     * JWT claim 또는 fallback으로 userId를 결정한다.
     *
     * <p>{@link com.stockmanagement.common.security.JwtAuthenticationFilter}가
     * {@code auth.setDetails(userId)}에 저장한 경우 DB 조회를 건너뛴다.
     * details가 없으면 {@code fallback}을 호출한다 (UserService.resolveUserId 등).
     *
     * @param auth     Spring Security 인증 객체 (null 허용)
     * @param fallback userId를 DB에서 조회하는 람다
     * @return 결정된 userId
     */
    public static Long resolveUserId(Authentication auth, Supplier<Long> fallback) {
        if (auth != null && auth.getDetails() instanceof Long userId) {
            return userId;
        }
        return fallback.get();
    }
}
