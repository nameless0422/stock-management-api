package com.stockmanagement.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Outbox purge 배치 단위 삭제 — 독립 트랜잭션.
 *
 * <p>{@link OutboxEventPurgeScheduler}의 루프에서 호출되어
 * 매 배치마다 별도 트랜잭션으로 커밋함으로써 잠금 점유 시간을 최소화한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPurgeProcessor {

    private final OutboxEventRepository repository;

    @Transactional
    public int deleteBatch(LocalDateTime threshold, int limit) {
        return repository.deleteBatchByPublishedAtBefore(threshold, limit);
    }
}
