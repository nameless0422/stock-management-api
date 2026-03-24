package com.stockmanagement.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 미발행 이벤트 중 재시도 횟수 상한 미만인 레코드를 최대 100건 조회한다.
     * 생성 순서대로 처리하여 이벤트 순서를 보장한다.
     */
    List<OutboxEvent> findTop100ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAtAsc(int maxRetry);

    /**
     * 발행 완료된 레코드 중 기준 시각 이전 것을 일괄 삭제한다 (purge용).
     *
     * @param before 삭제 기준 시각 (publishedAt < before)
     * @return 삭제된 레코드 수
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :before")
    int deleteByPublishedAtBefore(@Param("before") LocalDateTime before);

    /**
     * MAX_RETRY 초과로 영구 발행 실패된 레코드 수를 반환한다 (Prometheus 게이지용).
     *
     * @param maxRetry 재시도 상한 (이 값 이상이면 dead letter)
     */
    int countByPublishedAtIsNullAndRetryCountGreaterThanEqual(int maxRetry);
}
