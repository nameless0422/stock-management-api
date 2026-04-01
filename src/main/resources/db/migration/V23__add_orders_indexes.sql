-- =====================================================================
-- V23: orders 테이블 인덱스 추가
-- =====================================================================
-- user_id, status 컬럼은 자주 조회 조건으로 사용되지만 인덱스가 없어 풀 스캔 발생.
-- 적용 범위:
--   - idx_orders_user_id   : 사용자별 주문 조회 (GET /api/orders, 어드민 필터)
--   - idx_orders_status    : 상태별 집계 (대시보드 countByStatus)
--   - idx_orders_user_status: user_id + status 동시 필터 (OrderSpecification 복합 조건)
-- =====================================================================

ALTER TABLE orders ADD INDEX idx_orders_user_id (user_id);
ALTER TABLE orders ADD INDEX idx_orders_status (status);
ALTER TABLE orders ADD INDEX idx_orders_user_status (user_id, status);
