package com.stockmanagement.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 미발행 이벤트 중 재시도 가능한 레코드를 최대 {@code pageable.pageSize}건 조회한다.
     *
     * <p>조건:
     * <ul>
     *   <li>{@code publishedAt IS NULL} — 미발행</li>
     *   <li>{@code retryCount < maxRetry} — dead letter 제외</li>
     *   <li>{@code nextRetryAt IS NULL OR nextRetryAt <= :now} — 지수 백오프 대기 이벤트 제외</li>
     * </ul>
     * 생성 순서대로 처리하여 이벤트 순서를 보장한다.
     */
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.publishedAt IS NULL
              AND e.retryCount < :maxRetry
              AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findPendingEvents(@Param("maxRetry") int maxRetry,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);

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
