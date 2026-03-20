-- ===== 쿠폰 테이블 =====
CREATE TABLE coupons
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                 VARCHAR(50)    NOT NULL UNIQUE COMMENT '쿠폰 코드 (대소문자 구분)',
    name                 VARCHAR(100)   NOT NULL COMMENT '쿠폰명',
    description          VARCHAR(255)   COMMENT '쿠폰 설명',
    discount_type        VARCHAR(20)    NOT NULL COMMENT 'FIXED_AMOUNT | PERCENTAGE',
    discount_value       DECIMAL(19, 2) NOT NULL COMMENT '할인 금액 또는 퍼센트 값 (0~100)',
    minimum_order_amount DECIMAL(19, 2) COMMENT '최소 주문 금액 조건 (null=제한없음)',
    max_discount_amount  DECIMAL(19, 2) COMMENT '퍼센트 할인 시 최대 할인 금액 캡 (null=제한없음)',
    max_usage_count      INT            COMMENT '전체 사용 가능 횟수 (null=무제한)',
    usage_count          INT            NOT NULL DEFAULT 0 COMMENT '현재 사용 횟수',
    max_usage_per_user   INT            NOT NULL DEFAULT 1 COMMENT '사용자별 사용 가능 횟수',
    valid_from           DATETIME(6)    NOT NULL COMMENT '쿠폰 유효 시작일',
    valid_until          DATETIME(6)    NOT NULL COMMENT '쿠폰 유효 종료일',
    active               TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '활성화 여부',
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    KEY idx_coupons_code (code)
);

-- ===== 쿠폰 사용 이력 테이블 =====
CREATE TABLE coupon_usages
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id       BIGINT         NOT NULL,
    user_id         BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL UNIQUE COMMENT '주문당 쿠폰 1개',
    discount_amount DECIMAL(19, 2) NOT NULL COMMENT '실제 할인된 금액',
    used_at         DATETIME(6)    NOT NULL,
    KEY idx_coupon_usages_coupon_user (coupon_id, user_id),
    CONSTRAINT fk_coupon_usage_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_usage_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coupon_usage_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- ===== orders 테이블에 쿠폰 연동 컬럼 추가 =====
ALTER TABLE orders
    ADD COLUMN coupon_id        BIGINT         NULL COMMENT '적용된 쿠폰 ID',
    ADD COLUMN discount_amount  DECIMAL(19, 2) NOT NULL DEFAULT 0 COMMENT '쿠폰 할인 금액',
    ADD CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL;
