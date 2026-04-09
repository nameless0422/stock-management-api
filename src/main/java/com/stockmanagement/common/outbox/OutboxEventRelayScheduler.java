package com.stockmanagement.common.outbox;

import com.stockmanagement.common.lock.DistributedLock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 테이블의 미발행 이벤트를 주기적으로 읽어 Spring ApplicationEventPublisher로 relay한다.
 *
 * <p>relay는 트랜잭션 안에서 수행되므로, 기존 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 리스너들이 커밋 후 정상 실행된다.
 *
 * <p>{@code outbox.relay.enabled=true} 환경에서만 빈이 생성된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true")
public class OutboxEventRelayScheduler {

    /** 재시도 최대 횟수 — 초과 시 영구 제외. OutboxEventProcessor에서도 참조. */
    static final int MAX_RETRY = 5;

    private final OutboxEventRepository repository;
    private final OutboxEventProcessor processor;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("outbox.dead_letters", repository,
                        r -> r.countByPublishedAtIsNullAndRetryCountGreaterThanEqual(MAX_RETRY))
                .description("MAX_RETRY 초과로 영구 발행 실패된 Outbox 이벤트 수")
                .register(meterRegistry);
    }

    /**
     * 미발행 Outbox 이벤트를 배치로 읽어 건별로 독립 트랜잭션으로 처리한다.
     *
     * <p>각 이벤트는 {@link OutboxEventProcessor#processOne(Long)}에서 {@code REQUIRES_NEW}로
     * 처리되므로, 특정 이벤트 실패가 이미 성공한 이벤트의 커밋을 되돌리지 않는다.
     *
     * <p>분산 락({@code skipOnFailure=true})으로 멀티 인스턴스 중복 실행을 방지한다.
     */
    @DistributedLock(key = "'outbox:relay'", waitTime = 0, leaseTime = 30, skipOnFailure = true)
    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:5000}")
    public void relay() {
        List<OutboxEvent> pending = repository
                .findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(MAX_RETRY);

        if (pending.isEmpty()) return;

        log.debug("[Outbox] relay 대상: {}건", pending.size());

        // 건별 독립 트랜잭션: 중간 실패가 이전 성공 커밋을 롤백하지 않음
        for (OutboxEvent outbox : pending) {
            processor.processOne(outbox.getId());
        }
    }
}
