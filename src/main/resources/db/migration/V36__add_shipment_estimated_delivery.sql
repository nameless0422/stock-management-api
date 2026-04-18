-- #40: 배송 예상 도착일 추가
ALTER TABLE shipments
    ADD COLUMN estimated_delivery_at DATE NULL COMMENT '택배사 예상 도착일 (출고 시 입력)';
