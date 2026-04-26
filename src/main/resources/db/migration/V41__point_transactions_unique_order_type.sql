-- 동일 주문에 대한 동일 유형의 포인트 거래를 DB 레벨에서 한 번만 허용 (멱등성 보장)
-- NULL order_id는 수동 적립 등 주문 무관 거래이므로 제외 (MySQL에서 NULL != NULL)
ALTER TABLE point_transactions
    ADD UNIQUE INDEX uk_point_txn_order_type (order_id, type);
