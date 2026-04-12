-- 재고 변동 이력에 사유(note) 컬럼 추가
-- InventoryReceiveRequest·InventoryAdjustRequest 의 note 필드를 실제로 저장한다.
ALTER TABLE inventory_transactions
    ADD COLUMN note VARCHAR(255) NULL COMMENT '입고/조정 사유 (선택)';
