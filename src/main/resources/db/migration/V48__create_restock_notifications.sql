-- 재입고 알림 신청 테이블
CREATE TABLE restock_notifications (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_restock_notifications_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_restock_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_restock_notifications_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_restock_notifications_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
