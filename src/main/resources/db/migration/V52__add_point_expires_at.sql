ALTER TABLE point_transactions
    ADD COLUMN expires_at DATETIME NULL;

CREATE INDEX idx_point_txn_status_expires ON point_transactions (status, expires_at);
