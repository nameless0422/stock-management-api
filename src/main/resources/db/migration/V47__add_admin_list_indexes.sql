-- 어드민 목록 조회 페이지네이션 성능 개선
-- users, products, coupons 테이블에 created_at 인덱스 추가
-- (orders 테이블은 V27에서 이미 idx_orders_created_at 존재)

ALTER TABLE users
    ADD INDEX idx_users_created_at (created_at);

ALTER TABLE products
    ADD INDEX idx_products_created_at (created_at);

ALTER TABLE coupons
    ADD INDEX idx_coupons_created_at (created_at);
