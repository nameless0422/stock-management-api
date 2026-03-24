-- =============================================================
-- V21: 환불 이력(refunds) 테이블 생성
-- =============================================================

CREATE TABLE refunds
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id   BIGINT         NOT NULL COMMENT '환불 대상 결제 ID',
    order_id     BIGINT         NOT NULL COMMENT '환불 대상 주문 ID',
    amount       DECIMAL(19, 2) NOT NULL COMMENT '환불 금액',
    reason       VARCHAR(300)   NOT NULL COMMENT '환불 사유',
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/FAILED',
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6)    NULL COMMENT '처리 완료 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_payment_id (payment_id),  -- 결제당 1건 환불
    KEY idx_refunds_order_id (order_id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
