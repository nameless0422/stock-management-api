-- 배송 테이블
-- 주문 1건당 배송 1건 (order_id UNIQUE)
-- 상태: PREPARING(배송 준비) → SHIPPED(배송 중) → DELIVERED(배송 완료) | RETURNED(반품)
CREATE TABLE shipments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id         BIGINT       NOT NULL UNIQUE,
    status           VARCHAR(20)  NOT NULL,
    carrier          VARCHAR(100),
    tracking_number  VARCHAR(100),
    shipped_at       DATETIME(6),
    delivered_at     DATETIME(6),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,

    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
