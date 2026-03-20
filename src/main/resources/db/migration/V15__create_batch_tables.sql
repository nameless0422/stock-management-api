-- ===== 배치 처리 테이블 =====

-- 일별 주문·매출 통계 (DailyOrderStatsScheduler가 매일 자정 1분에 집계)
CREATE TABLE daily_order_stats (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date        DATE           NOT NULL UNIQUE COMMENT '집계 기준일',
    total_orders     INT            NOT NULL DEFAULT 0 COMMENT '전일 전체 주문 수',
    confirmed_orders INT            NOT NULL DEFAULT 0 COMMENT '전일 결제 완료 주문 수',
    cancelled_orders INT            NOT NULL DEFAULT 0 COMMENT '전일 취소 주문 수',
    total_revenue    DECIMAL(19, 2) NOT NULL DEFAULT 0 COMMENT '전일 매출액 (CONFIRMED 기준, 쿠폰 할인 차감)',
    created_at       DATETIME(6)    NOT NULL COMMENT '집계 생성 시각'
);

-- 일별 재고 스냅샷 (InventorySnapshotScheduler가 매일 자정 5분에 전체 재고 캡처)
CREATE TABLE daily_inventory_snapshots (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    inventory_id  BIGINT      NOT NULL COMMENT 'inventory.id FK',
    snapshot_date DATE        NOT NULL COMMENT '스냅샷 기준일',
    on_hand       INT         NOT NULL COMMENT '창고 실물 재고',
    reserved      INT         NOT NULL COMMENT '주문 예약 재고 (미결제)',
    allocated     INT         NOT NULL COMMENT '출고 확정 재고 (결제 완료)',
    available     INT         NOT NULL COMMENT '주문 가능 재고 = onHand - reserved - allocated',
    created_at    DATETIME(6) NOT NULL COMMENT '스냅샷 생성 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_inv_date (inventory_id, snapshot_date),
    CONSTRAINT fk_snapshot_inventory
        FOREIGN KEY (inventory_id) REFERENCES inventory (id) ON DELETE CASCADE
);
