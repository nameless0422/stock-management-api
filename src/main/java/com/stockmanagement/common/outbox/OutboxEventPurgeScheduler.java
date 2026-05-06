package com.stockmanagement.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 발행 완료된 Outbox 레코드를 주기적으로 삭제한다.
 *
 * <p>보존 기간({@code outbox.purge.retention-days}) 이상 경과한 완료 레코드를 삭제하여
 * {@code outbox_events} 테이블이 무한 증가하지 않도록 한다.
 *
 * <p>단일 대용량 DELETE 대신 {@value BATCH_SIZE}건씩 루프 삭제하여
 * 장시간 테이블 잠금으로 인한 relay·INSERT 블로킹을 방지한다.
 *
 * <p>{@code outbox.purge.enabled=true} 환경에서만 빈이 생성된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.purge.enabled", havingValue = "true")
public class OutboxEventPurgeScheduler {

    private static final int BATCH_SIZE = 1_000;

    private final OutboxEventRepository repository;
    private final OutboxEventPurgeProcessor purgeProcessor;

    @Value("${outbox.purge.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${outbox.purge.cron:0 0 3 * * *}")
    public void purge() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int totalDeleted = 0;
        int deleted;

        // 배치 루프: 매 이터레이션마다 독립 트랜잭션으로 커밋 → 장시간 잠금 방지
        do {
            deleted = purgeProcessor.deleteBatch(threshold, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);

        if (totalDeleted > 0) {
            log.info("[Outbox] purge 완료: {}건 삭제 (보존 기간: {}일, 기준: {})",
                    totalDeleted, retentionDays, threshold);
        }
    }
}
