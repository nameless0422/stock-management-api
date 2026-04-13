-- =====================================================================
-- V33: created_at / updated_at DEFAULT CURRENT_TIMESTAMP 누락 수정
-- =====================================================================
-- 아래 6개 테이블이 Hibernate @CreationTimestamp/@UpdateTimestamp에만 의존하고
-- DDL 레벨 DEFAULT가 없어, bulk INSERT 또는 DB 직접 조작 시 NOT NULL 위반 가능.
-- 다른 모든 테이블(V1~V6 등)은 DEFAULT CURRENT_TIMESTAMP(6)가 정의되어 있음.
-- =====================================================================

-- payments (V4)
ALTER TABLE payments
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

-- shipments (V11)
ALTER TABLE shipments
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

-- delivery_addresses (V12)
ALTER TABLE delivery_addresses
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

-- cart_items (V10)
ALTER TABLE cart_items
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

-- product_images (V17)
ALTER TABLE product_images
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- user_coupons (V22)
ALTER TABLE user_coupons
    MODIFY issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
