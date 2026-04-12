package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 저장소 (Redis 기반).
 *
 * <p>키 구조:
 * <ul>
 *   <li>{@code refresh:token:{uuid}} → username (TTL: 30일)
 *   <li>{@code refresh:user:{username}} → Set&lt;uuid&gt; (역색인, TTL 없음)
 * </ul>
 *
 * <p>역색인({@code refresh:user:*})은 회원 탈퇴 시 사용자의 모든 토큰을 일괄 폐기하는 데 쓰인다.
 * Set 내 UUID는 실제 토큰 키 만료 후 남을 수 있으나, {@code revokeAll} 호출 시 존재하지 않는
 * 키 삭제는 no-op이므로 안전하다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String USER_PREFIX  = "refresh:user:";
    private static final long TTL_DAYS = 30;

    private final RedissonClient redissonClient;

    /**
     * Refresh Token 발급. UUID 생성 후 Redis에 저장하고 토큰 문자열을 반환한다.
     * 사용자별 역색인({@code refresh:user:{username}})에도 UUID를 추가한다.
     */
    public String issue(String username) {
        String token = UUID.randomUUID().toString();
        redissonClient.<String>getBucket(TOKEN_PREFIX + token)
                .set(username, TTL_DAYS, TimeUnit.DAYS);
        // 역색인: 회원 탈퇴 시 일괄 폐기를 위해 username → token UUID 매핑 유지
        redissonClient.<String>getSet(USER_PREFIX + username).add(token);
        return token;
    }

    /**
     * Refresh Token 소비(rotation). 토큰을 삭제하고 저장된 username을 반환한다.
     * 역색인에서도 UUID를 제거한다.
     *
     * @throws BusinessException 토큰이 존재하지 않거나 만료된 경우
     */
    public String consume(String token) {
        RBucket<String> bucket = redissonClient.getBucket(TOKEN_PREFIX + token);
        String username = bucket.getAndDelete();
        if (username == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        redissonClient.<String>getSet(USER_PREFIX + username).remove(token);
        return username;
    }

    /** Refresh Token 즉시 무효화 (로그아웃 시 호출). */
    public void revoke(String token) {
        RBucket<String> bucket = redissonClient.getBucket(TOKEN_PREFIX + token);
        String username = (String) bucket.get();
        bucket.delete();
        if (username != null) {
            redissonClient.<String>getSet(USER_PREFIX + username).remove(token);
        }
    }

    /**
     * 사용자의 모든 Refresh Token을 폐기한다 (회원 탈퇴 시 호출).
     *
     * <p>역색인({@code refresh:user:{username}})에서 UUID 목록을 읽어
     * 각 토큰 키를 삭제한 후 역색인 자체도 삭제한다.
     *
     * @param username 폐기 대상 사용자명
     */
    public void revokeAll(String username) {
        var userSet = redissonClient.<String>getSet(USER_PREFIX + username);
        for (String token : userSet) {
            redissonClient.getBucket(TOKEN_PREFIX + token).delete();
        }
        userSet.delete();
    }
}
