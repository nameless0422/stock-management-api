-- 주문 아이템별 부분 취소 지원을 위한 상태 컬럼 추가
ALTER TABLE order_items
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancelled_at DATETIME(6) NULL;
