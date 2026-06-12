-- 상품 옵션/변형(variants) 시스템 도입
-- Product 1:N ProductVariant, Inventory FK를 product_id → variant_id로 전환

-- 1) product_variants 테이블 생성
CREATE TABLE product_variants (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    product_id  BIGINT         NOT NULL,
    option_name VARCHAR(100)   NOT NULL,
    sku         VARCHAR(100)   NOT NULL,
    price       DECIMAL(15,2)  NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_variant_sku (sku),
    INDEX idx_variant_product (product_id),
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2) 기존 상품마다 기본 variant 자동 생성
INSERT INTO product_variants (product_id, option_name, sku, price, status, created_at, updated_at)
SELECT id, '기본', sku, price, status, NOW(6), NOW(6) FROM products;

-- 3) inventory에 variant_id 컬럼 추가 + 데이터 채우기
ALTER TABLE inventory ADD COLUMN variant_id BIGINT NULL AFTER product_id;

UPDATE inventory i
    INNER JOIN product_variants pv ON pv.product_id = i.product_id
SET i.variant_id = pv.id;

ALTER TABLE inventory
    MODIFY COLUMN variant_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_inventory_variant (variant_id),
    ADD CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id);

-- product_id UNIQUE → 일반 INDEX 전환 (컬럼 유지, 읽기 전용 JOIN 호환)
-- 먼저 일반 인덱스 추가 후 UNIQUE 인덱스 제거 (FK가 새 인덱스로 전환)
ALTER TABLE inventory
    ADD INDEX idx_inventory_product (product_id),
    DROP INDEX uk_inventory_product;

-- 4) order_items에 variant_id 추가
ALTER TABLE order_items ADD COLUMN variant_id BIGINT NULL AFTER product_id;

UPDATE order_items oi
    INNER JOIN product_variants pv ON pv.product_id = oi.product_id AND pv.option_name = '기본'
SET oi.variant_id = pv.id;

ALTER TABLE order_items
    MODIFY COLUMN variant_id BIGINT NOT NULL,
    ADD INDEX idx_order_items_variant (variant_id),
    ADD CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id);

-- 5) cart_items에 variant_id 추가 + UK 변경
ALTER TABLE cart_items ADD COLUMN variant_id BIGINT NULL AFTER product_id;

UPDATE cart_items ci
    INNER JOIN product_variants pv ON pv.product_id = ci.product_id AND pv.option_name = '기본'
SET ci.variant_id = pv.id;

ALTER TABLE cart_items
    MODIFY COLUMN variant_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    DROP INDEX uk_cart_user_product,
    ADD UNIQUE KEY uk_cart_user_variant (user_id, variant_id);
