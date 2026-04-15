-- =====================================================================
-- V30: charset · collation 통일
-- =====================================================================
-- 대상 테이블:
--   payments              (V4)  — ENGINE/CHARSET/COLLATE 모두 미정의
--   daily_order_stats     (V15) — ENGINE/CHARSET/COLLATE 모두 미정의
--   daily_inventory_snapshots (V15) — ENGINE/CHARSET/COLLATE 모두 미정의
--   outbox_events         (V18) — CHARSET=utf8mb4이나 COLLATE 미정의
--
-- 다른 모든 테이블은 utf8mb4_unicode_ci 로 통일되어 있음.
-- collation 불일치 상태에서 JOIN 시 "Illegal mix of collations" 에러 또는
-- 인덱스 미사용(payments JOIN orders 등)이 발생할 수 있음.
-- =====================================================================

ALTER TABLE payments
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE daily_order_stats
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE daily_inventory_snapshots
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE outbox_events
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
