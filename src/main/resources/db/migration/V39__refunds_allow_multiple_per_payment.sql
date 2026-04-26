-- 결제당 여러 번 부분 취소를 허용하기 위해 UNIQUE 제약 제거 (V21에서 uk_refunds_payment_id로 생성)
-- FK(fk_refunds_payment)가 uk_refunds_payment_id를 사용하므로, 단독 DROP이 아닌 단일 ALTER TABLE로
-- 인덱스 교체 (DROP + ADD를 원자적으로 처리 → FK 지원 인덱스 연속성 보장)
ALTER TABLE refunds
    DROP INDEX uk_refunds_payment_id,
    ADD INDEX idx_refunds_payment_created (payment_id, created_at);
