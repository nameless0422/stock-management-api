-- =====================================================================
-- V27: 누락 인덱스 추가
-- =====================================================================
-- orders.created_at:
--   DailyOrderStatsScheduler, AdminController 통계 집계에서 날짜 범위 필터로
--   created_at을 사용하지만 단일 인덱스가 없어 풀 스캔 발생.
-- point_transactions (user_id, order_id):
--   PointService.refundByOrder()가 (user_id + order_id) 두 조건으로 조회하나
--   단일 인덱스 두 개로는 복합 조건을 커버하지 못해 filesort 발생.
--   user_id 단일 인덱스(V20)는 이 복합 인덱스의 prefix로 중복되므로 제거.
-- =====================================================================

-- orders: 기간 검색 및 통계 집계
ALTER TABLE orders
    ADD INDEX idx_orders_created_at (created_at);

-- point_transactions: user_id + order_id 복합 조건 조회
--   기존 단일 인덱스(user_id)는 복합 인덱스 prefix로 대체
ALTER TABLE point_transactions
    DROP INDEX idx_point_transactions_user_id,
    ADD INDEX idx_pt_user_order (user_id, order_id);
