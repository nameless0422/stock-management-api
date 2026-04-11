package com.stockmanagement.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    /** 개발용 기본 시크릿 — 운영 환경에서 이 값이 사용되면 보안 위험. */
    private static final String DEV_DEFAULT_SECRET =
            "stock-management-secret-key-for-development-only";

    @Value("${jwt.secret}")
    private String secretKeyStr;

    @Value("${jwt.token-validity-in-seconds:86400}")
    private long tokenValidityInSeconds;

    private SecretKey secretKey;

    /**
     * 시작 시 JWT 시크릿을 초기화하고 보안 검사를 수행한다.
     *
     * <p>JWT_SECRET 환경변수 미설정으로 개발용 기본값이 사용되는 경우 ERROR 로그를 출력한다.
     * 공격자가 기본값을 알고 있으므로 운영 배포 전 반드시 교체해야 한다.
     */
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyStr.getBytes(StandardCharsets.UTF_8));

        if (DEV_DEFAULT_SECRET.equals(secretKeyStr)) {
            log.error("╔══════════════════════════════════════════════════╗");
            log.error("║ [SECURITY WARNING] JWT 개발용 기본 시크릿 사용 중  ║");
            log.error("║ JWT_SECRET 환경변수를 설정하지 않으면               ║");
            log.error("║ 공격자가 임의 토큰을 위조할 수 있습니다.            ║");
            log.error("╚══════════════════════════════════════════════════╝");
        }
    }

    /**
     * JWT 액세스 토큰을 생성한다.
     *
     * <p>{@code userId}를 클레임에 포함하여 서비스 레이어에서 DB 조회 없이 userId를 사용할 수 있게 한다.
     */
    public String createToken(String username, String role, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenValidityInSeconds * 1000);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** userId 없는 토큰 생성 — 테스트/레거시 호환용 */
    public String createToken(String username, String role) {
        return createToken(username, role, null);
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * JWT 토큰에서 userId 클레임을 추출한다.
     * 구 버전 토큰처럼 클레임이 없으면 null을 반환한다.
     */
    public Long getUserId(String token) {
        try {
            return parseClaims(token).get("userId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT 토큰: {}", e.getMessage());
            return false;
        }
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    /** 토큰 만료까지 남은 시간(초). 이미 만료된 경우 0을 반환. */
    public long getRemainingSeconds(String token) {
        Date expiry = parseClaims(token).getExpiration();
        long remaining = (expiry.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    public long getTokenValidityInSeconds() {
        return tokenValidityInSeconds;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
