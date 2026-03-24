-- =============================================================
-- V20: 포인트/적립금 시스템 테이블 생성
-- =============================================================

-- 사용자별 포인트 잔액 테이블
-- 동시성 제어: 비관적 락(SELECT FOR UPDATE)으로 잔액 일관성 보장
CREATE TABLE user_points
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT '사용자 ID',
    balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '현재 포인트 잔액',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_points_user_id (user_id),
    CONSTRAINT fk_user_points_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 포인트 변동 이력 테이블
CREATE TABLE point_transactions
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '사용자 ID',
    amount      BIGINT       NOT NULL COMMENT '변동 금액 (양수=적립/환불, 음수=사용/소멸)',
    type        VARCHAR(20)  NOT NULL COMMENT 'EARN/USE/REFUND/EXPIRE',
    description VARCHAR(200) NOT NULL COMMENT '변동 사유',
    order_id    BIGINT       NULL COMMENT '연관 주문 ID (있는 경우)',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_point_transactions_user_id (user_id),
    KEY idx_point_transactions_order_id (order_id),
    CONSTRAINT fk_point_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- orders 테이블에 포인트 사용 금액 컬럼 추가
ALTER TABLE orders
    ADD COLUMN used_points BIGINT NOT NULL DEFAULT 0 COMMENT '주문 시 사용한 포인트';
