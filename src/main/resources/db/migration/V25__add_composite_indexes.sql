-- =====================================================================
-- V25: 복합 인덱스 추가
-- =====================================================================
-- inventory_transactions: inventory_id 단일 인덱스(V6)만 존재.
--   이력 조회 시 ORDER BY created_at DESC가 항상 동반되므로
--   (inventory_id, created_at) 복합 인덱스로 filesort 제거.
-- =====================================================================

ALTER TABLE inventory_transactions
    ADD INDEX idx_inv_tx_inventory_created (inventory_id, created_at);
