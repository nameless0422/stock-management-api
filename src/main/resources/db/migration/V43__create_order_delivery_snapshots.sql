CREATE TABLE order_delivery_snapshots (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    recipient     VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    zip_code      VARCHAR(10)  NOT NULL,
    address1      VARCHAR(200) NOT NULL,
    address2      VARCHAR(100),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_order_delivery_snapshots_order_id UNIQUE (order_id),
    CONSTRAINT fk_order_delivery_snapshots_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
