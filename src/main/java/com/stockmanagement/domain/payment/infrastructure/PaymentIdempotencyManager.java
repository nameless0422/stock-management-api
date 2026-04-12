package com.stockmanagement.domain.payment.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.domain.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 결제 멱등성 키 관리.
 *
 * <p>confirm/cancel 요청의 중복 처리를 방지한다.
 * SETNX(setIfAbsent)로 "PROCESSING" 상태를 원자적으로 선점한 뒤,
 * 처리 완료 시 결과 JSON을 TTL과 함께 저장한다.
 *
 * <p>키 형식: {@code idempotency:payment:{operation}:{identifier}}
 * <ul>
 *   <li>confirm: {@code idempotency:payment:confirm:{tossOrderId}}
 *   <li>cancel:  {@code idempotency:payment:cancel:{paymentKey}}
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentIdempotencyManager {

    private static final String KEY_PREFIX = "idempotency:payment:";
    private static final String PROCESSING = "PROCESSING";
    /** 처리 중 상태 유지 시간 — 이 시간 안에 처리가 완료되지 않으면 자동 해제 */
    private static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    /** 완료 결과 보존 시간 — 이 기간 내 재요청은 캐시 응답 반환 */
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /**
     * 처리 중 상태를 원자적으로 선점한다 (SETNX).
     *
     * @param key 멱등성 키 (operation:identifier)
     * @return true = 선점 성공 (이 요청이 처음), false = 이미 처리 중
     */
    public boolean tryAcquire(String key) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + key);
        return bucket.setIfAbsent(PROCESSING, PROCESSING_TTL);
    }

    /**
     * 완료된 처리 결과가 Redis에 있으면 반환한다.
     * 키가 없거나 PROCESSING 상태이면 {@link Optional#empty()} 반환.
     *
     * @param key 멱등성 키
     * @return 이미 완료된 결과, 없으면 empty
     */
    public Optional<PaymentResponse> getIfCompleted(String key) {
        RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + key);
        String value = bucket.get();
        if (value == null || PROCESSING.equals(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, PaymentResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("멱등성 키 역직렬화 실패 — 캐시 무효화: key={}", KEY_PREFIX + key, e);
            bucket.delete();
            return Optional.empty();
        }
    }

    /**
     * 처리 완료 후 결과를 Redis에 캐싱한다. TTL {@value RESULT_TTL_HOURS}시간.
     *
     * @param key      멱등성 키
     * @param response 저장할 결과
     */
    public void complete(String key, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            RBucket<String> bucket = redissonClient.getBucket(KEY_PREFIX + key);
            bucket.set(json, RESULT_TTL);
        } catch (JsonProcessingException e) {
            log.warn("멱등성 키 직렬화 실패 — 캐시 저장 생략: key={}", KEY_PREFIX + key, e);
        }
    }

    /**
     * 처리 실패 시 키를 삭제해 재시도를 허용한다.
     *
     * @param key 멱등성 키
     */
    public void release(String key) {
        redissonClient.getBucket(KEY_PREFIX + key).delete();
    }
}
