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
 * <p>{@code outbox.purge.enabled=true} 환경에서만 빈이 생성된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.purge.enabled", havingValue = "true")
public class OutboxEventPurgeScheduler {

    private final OutboxEventRepository repository;

    @Value("${outbox.purge.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${outbox.purge.cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int deleted = repository.deleteByPublishedAtBefore(threshold);
        if (deleted > 0) {
            log.info("[Outbox] purge 완료: {}건 삭제 (보존 기간: {}일, 기준: {})",
                    deleted, retentionDays, threshold);
        }
    }
}
