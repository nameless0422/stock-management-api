-- Payment table for TossPayments integration
-- Tracks the full lifecycle of each payment attempt: PENDING → DONE / FAILED → CANCELLED
CREATE TABLE payments
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,

    -- Reference to our internal order (no FK cascade — payment and order are updated independently)
    order_id        BIGINT         NOT NULL,

    -- Assigned by TossPayments after successful confirmation; NULL until then
    payment_key     VARCHAR(200),

    -- The orderId we sent to TossPayments; unique per payment attempt
    toss_order_id   VARCHAR(64)    NOT NULL,

    -- Amount agreed at prepare time; used for server-side verification
    amount          DECIMAL(12, 2) NOT NULL,

    -- Payment lifecycle status: PENDING | DONE | CANCELLED | FAILED | PARTIAL_CANCELLED
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',

    -- Payment method returned by TossPayments (e.g. '카드', '가상계좌', '계좌이체')
    method          VARCHAR(50),

    -- Timestamps from TossPayments response
    requested_at    DATETIME(6),
    approved_at     DATETIME(6),

    -- Populated on cancellation
    cancel_reason   VARCHAR(200),

    -- Populated when TossPayments rejects the payment
    failure_code    VARCHAR(50),
    failure_message VARCHAR(200),

    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_toss_order_id (toss_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
