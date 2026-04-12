CREATE TABLE inventory_transactions
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    inventory_id       BIGINT      NOT NULL,
    type               VARCHAR(30) NOT NULL,
    quantity           INT         NOT NULL,
    snapshot_on_hand   INT         NOT NULL,
    snapshot_reserved  INT         NOT NULL,
    snapshot_allocated INT         NOT NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_inv_tx_inventory (inventory_id),
    CONSTRAINT fk_inv_tx_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
