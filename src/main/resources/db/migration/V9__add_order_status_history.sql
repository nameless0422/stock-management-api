-- 주문 상태 변경 이력 테이블
CREATE TABLE order_status_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT       NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_by  VARCHAR(100),
    note        VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_osh_order_id (order_id)
);
