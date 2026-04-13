-- =====================================================================
-- V34: outbox_events 지수 백오프 지원
-- =====================================================================
-- 기존 relay 쿼리는 failedAt 컬럼이 있음에도 미발행 이벤트를
-- 5초 간격으로 무조건 재시도 → 다운스트림 장애 시 연속 폭격 발생.
-- next_retry_at(NULL = 즉시 재시도 가능)을 추가하고
-- recordFailure() 호출 시 지수 백오프로 산출: 30s × 2^retryCount (최대 1h).
-- relay 쿼리에서 next_retry_at <= NOW() 조건으로 대기 중인 이벤트만 선택.
-- 기존 레코드는 NULL → 즉시 재시도 대상 유지(하위 호환).
-- =====================================================================

ALTER TABLE outbox_events
    ADD COLUMN next_retry_at DATETIME(6) NULL AFTER failed_at,
    ADD INDEX idx_outbox_next_retry (published_at, retry_count, next_retry_at);
