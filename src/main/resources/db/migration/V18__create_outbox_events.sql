-- Transactional Outbox 패턴: 이벤트 유실 방지
-- 비즈니스 트랜잭션과 같은 TX에 저장 → OutboxEventRelayScheduler가 발행
CREATE TABLE outbox_events (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    event_type  VARCHAR(50)     NOT NULL COMMENT '이벤트 종류 (OutboxEventType enum)',
    payload     TEXT            NOT NULL COMMENT '이벤트 데이터 (JSON)',
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6)   NULL     COMMENT 'NULL = 미발행',
    retry_count INT             NOT NULL DEFAULT 0,
    failed_at   DATETIME(6)    NULL     COMMENT '최근 발행 실패 시각',
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published_at, retry_count, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
