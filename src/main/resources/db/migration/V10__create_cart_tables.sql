-- 장바구니 아이템 테이블
-- 사용자별 상품당 1개 행 유지 (user_id + product_id UNIQUE)
CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    updated_at DATETIME(6)    NOT NULL,

    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (id)
);
