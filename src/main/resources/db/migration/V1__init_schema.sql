-- =============================================================
-- V1: 초기 스키마 — 상품(products) 마스터 테이블
-- 이후 재고(inventory), 주문(order), 결제(payment) 테이블은
-- V2, V3 마이그레이션에서 추가된다.
-- =============================================================

-- 상품 마스터 테이블
-- 재고·주문 등 다른 테이블에서 FK로 참조하는 핵심 마스터 데이터
CREATE TABLE products
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)   NOT NULL,                -- 상품명
    description TEXT,                                   -- 상품 상세 설명 (선택)
    price       DECIMAL(12, 2) NOT NULL,                -- 판매가 (최대 12자리, 소수점 2자리)
    sku         VARCHAR(100)   NOT NULL,                -- 재고 관리 코드 (고유값)
    category    VARCHAR(100),                           -- 상품 카테고리 (선택)
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE', -- 판매 상태: ACTIVE / INACTIVE / DISCONTINUED
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku)                   -- SKU 중복 방지
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
