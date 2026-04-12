-- =====================================================================
-- V3: orders, order_items 테이블 생성
-- =====================================================================
-- orders: 주문 헤더 (userId, 상태, 총액, 멱등성 키)
-- order_items: 주문 내 상품 항목 (product, 수량, 단가, 소계)
-- =====================================================================

CREATE TABLE orders
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,                         -- User 도메인 미구현 — FK 없이 Long만 저장
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',       -- OrderStatus enum
    total_amount    DECIMAL(12, 2) NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,                         -- 클라이언트 발급 UUID, 중복 주문 방지
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_idempotency_key (idempotency_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_items
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,                              -- 주문 당시 단가 (가격 변경 이력 보존)
    subtotal   DECIMAL(12, 2) NOT NULL,                              -- unit_price * quantity
    created_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
