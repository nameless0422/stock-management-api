package com.stockmanagement.common.security;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
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

    private static final String TOKEN_PREFIX    = "refresh:token:";
    private static final String USER_PREFIX     = "refresh:user:";
    private static final String CONSUMED_PREFIX = "refresh:consumed:";
    private static final long TTL_DAYS          = 30;
    /** 소비 완료 마커 TTL — 이 시간 내 재사용 시 탈취 감지 */
    private static final long CONSUMED_TTL_MINUTES = 5;

    // revokeAll: Set 읽기 → 토큰 키 일괄 삭제 → Set 삭제를 Lua로 원자 실행
    // 순회 도중 issue()가 호출되어도 새 토큰이 살아남지 않는다.
    private static final String REVOKE_ALL_SCRIPT =
            "local members = redis.call('SMEMBERS', KEYS[1]) " +
            "for _, uuid in ipairs(members) do " +
            "    redis.call('DEL', ARGV[1] .. uuid) " +
            "end " +
            "redis.call('DEL', KEYS[1]) " +
            "return #members";

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
        var userSet = redissonClient.<String>getSet(USER_PREFIX + username);
        userSet.add(token);
        // 역색인 Set에 TTL 설정 — 토큰 만료(30일)보다 약간 길게 설정하여 메모리 누수 방지
        userSet.expire(java.time.Duration.ofDays(TTL_DAYS + 5));
        return token;
    }

    /**
     * Refresh Token 소비(rotation). 토큰을 삭제하고 저장된 username을 반환한다.
     * 역색인에서도 UUID를 제거한다.
     *
     * <p>Refresh Token Family Rotation: 이미 소비된 토큰이 재사용되면 탈취 징후로 판단하여
     * 해당 사용자의 모든 세션을 즉시 무효화한다.
     *
     * @throws BusinessException 토큰이 존재하지 않거나 만료된 경우
     */
    public String consume(String token) {
        // 소비 완료 마커 확인 — 이미 소비된 토큰 재사용 = 탈취 징후
        RBucket<String> consumedBucket = redissonClient.getBucket(CONSUMED_PREFIX + token);
        String consumedUsername = (String) consumedBucket.get();
        if (consumedUsername != null) {
            // 탈취된 토큰으로 인한 replay attack — 해당 사용자의 모든 세션 무효화
            revokeAll(consumedUsername);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RBucket<String> bucket = redissonClient.getBucket(TOKEN_PREFIX + token);
        String username = bucket.getAndDelete();
        if (username == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        redissonClient.<String>getSet(USER_PREFIX + username).remove(token);

        // 소비 완료 마커 저장 (5분 TTL) — 이 시간 내 재사용 시 탈취로 감지
        consumedBucket.set(username, CONSUMED_TTL_MINUTES, TimeUnit.MINUTES);

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
     * 사용자의 모든 Refresh Token을 폐기한다 (회원 탈퇴·비밀번호 변경·탈취 감지 시 호출).
     *
     * <p>Lua 스크립트로 Set 읽기 → 토큰 키 일괄 삭제 → Set 삭제를 원자적으로 실행한다.
     * 개별 삭제 루프 방식에서는 순회 도중 {@code issue()}가 호출되면 새 토큰이 살아남는 버그가 있다.
     *
     * @param username 폐기 대상 사용자명
     */
    public void revokeAll(String username) {
        List<Object> keys = List.of(USER_PREFIX + username);
        redissonClient.getScript(LongCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                REVOKE_ALL_SCRIPT,
                RScript.ReturnType.INTEGER,
                keys,
                TOKEN_PREFIX
        );
    }
}
