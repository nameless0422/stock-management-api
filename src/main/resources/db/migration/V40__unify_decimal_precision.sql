-- DECIMAL precision 불일치 통일: DECIMAL(12,2) / DECIMAL(19,2) → DECIMAL(15,2)
-- 최대 999억원까지 표현 가능 (운영 요건 충족)
ALTER TABLE products
    MODIFY COLUMN price DECIMAL(15, 2) NOT NULL;

ALTER TABLE orders
    MODIFY COLUMN total_amount    DECIMAL(15, 2) NOT NULL,
    MODIFY COLUMN discount_amount DECIMAL(15, 2) NOT NULL;

ALTER TABLE order_items
    MODIFY COLUMN unit_price DECIMAL(15, 2) NOT NULL,
    MODIFY COLUMN subtotal   DECIMAL(15, 2) NOT NULL;

ALTER TABLE payments
    MODIFY COLUMN amount           DECIMAL(15, 2) NOT NULL,
    MODIFY COLUMN cancelled_amount DECIMAL(15, 2);

ALTER TABLE coupons
    MODIFY COLUMN discount_value        DECIMAL(15, 2) NOT NULL,
    MODIFY COLUMN minimum_order_amount  DECIMAL(15, 2),
    MODIFY COLUMN max_discount_amount   DECIMAL(15, 2);

ALTER TABLE coupon_usages
    MODIFY COLUMN discount_amount DECIMAL(15, 2) NOT NULL;

ALTER TABLE daily_order_stats
    MODIFY COLUMN total_revenue DECIMAL(15, 2) NOT NULL;

ALTER TABLE refunds
    MODIFY COLUMN amount DECIMAL(15, 2) NOT NULL;

ALTER TABLE cart_items
    MODIFY COLUMN saved_price DECIMAL(15, 2);
