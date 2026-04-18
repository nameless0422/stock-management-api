ALTER TABLE cart_items
    ADD COLUMN saved_price DECIMAL(12, 2) NULL COMMENT '장바구니 담은 시점의 단가 (가격 변동 감지용)';
