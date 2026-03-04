-- =============================================================
-- V2: 재고(inventory) 테이블
-- products 테이블을 FK로 참조한다 (V1 선행 필요).
-- 상품 1개 당 재고 레코드 1개 (1:1 관계).
-- =============================================================

CREATE TABLE inventory
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,                 -- 상품 FK (1:1)
    on_hand    INT         NOT NULL DEFAULT 0,        -- 창고 실물 재고
    reserved   INT         NOT NULL DEFAULT 0,        -- 주문 생성으로 잡아둔 재고 (미결제)
    allocated  INT         NOT NULL DEFAULT 0,        -- 결제 완료 후 확정된 재고
    version    INT         NOT NULL DEFAULT 0,        -- 낙관적 락용 버전 번호
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_product (product_id),    -- 상품당 재고 레코드는 반드시 1개
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
