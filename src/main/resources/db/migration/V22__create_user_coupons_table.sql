-- 사용자 쿠폰 발급 테이블 (관리자 → 사용자 발급)
CREATE TABLE user_coupons
(
    id        BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT      NOT NULL,
    coupon_id BIGINT      NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_coupons (user_id, coupon_id),
    CONSTRAINT fk_user_coupon_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
);
