-- =====================================================================
-- V31: 누락 인덱스 추가
-- =====================================================================
-- orders (status, created_at):
--   DailyOrderStatsScheduler가 매일 자정 countByStatusAndCreatedAtBetween()을
--   CONFIRMED·CANCELLED 각 1회씩 실행.
--   쿼리: WHERE status = ? AND created_at >= ? AND created_at < ?
--   기존 idx_orders_status(status) + idx_orders_created_at(created_at) 단일 인덱스로는
--   옵티마이저가 한쪽만 택해 나머지 조건을 post-filter 처리 → filesort 발생.
--   (status, created_at) 복합 인덱스로 커버링 인덱스 스캔 가능.
--
-- products (status):
--   ProductRepository 전 조회 쿼리에 WHERE status = 'ACTIVE' 조건 포함.
--   V1~V29 어디에도 status 인덱스 없어 products 풀스캔 발생.
--
-- coupons (valid_until, active):
--   CouponExpiryScheduler: WHERE valid_until < :now AND active = true
--   매일 새벽 1시 실행. 인덱스 없으면 coupons 풀스캔.
-- =====================================================================

-- #35: 통계 쿼리 커버링 인덱스
ALTER TABLE orders
    ADD INDEX idx_orders_status_created (status, created_at);

-- #37: 상품 상태 필터 인덱스
ALTER TABLE products
    ADD INDEX idx_products_status (status);

-- #36: 만료 쿠폰 조회 인덱스
ALTER TABLE coupons
    ADD INDEX idx_coupons_expiry (valid_until, active);
