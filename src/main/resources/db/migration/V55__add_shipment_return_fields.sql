ALTER TABLE shipments
    MODIFY COLUMN status VARCHAR(30) NOT NULL COMMENT '배송 상태 (RETURN_REQUESTED 수용)',
    ADD COLUMN return_reason VARCHAR(500) NULL COMMENT '반품 사유',
    ADD COLUMN return_requested_at DATETIME(6) NULL COMMENT '반품 신청 일시';
