-- payments.order_id UNIQUE 제약 추가
-- 동일 주문에 대한 중복 결제 레코드 생성 방지 (prepare() 동시성 제어)
ALTER TABLE payments ADD UNIQUE KEY uk_payments_order_id (order_id);
