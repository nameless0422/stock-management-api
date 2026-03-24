-- =============================================================
-- V19: 리뷰(reviews) + 위시리스트(wishlist_items) 테이블 생성
-- =============================================================

-- 상품 리뷰 테이블
-- 실구매자(status=CONFIRMED 주문 보유)만 작성 가능, 상품당 1인 1리뷰
CREATE TABLE reviews
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL COMMENT '리뷰 대상 상품 ID',
    user_id    BIGINT       NOT NULL COMMENT '작성자 ID',
    rating     TINYINT      NOT NULL COMMENT '별점 1-5',
    title      VARCHAR(100) NOT NULL COMMENT '리뷰 제목',
    content    TEXT         NOT NULL COMMENT '리뷰 본문',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_product_user (product_id, user_id),  -- 1인 1리뷰 제약
    KEY idx_reviews_product_id (product_id),
    KEY idx_reviews_user_id (user_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 위시리스트 테이블
-- 로그인 사용자가 관심 상품을 저장, 상품당 1건
CREATE TABLE wishlist_items
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT '사용자 ID',
    product_id BIGINT      NOT NULL COMMENT '관심 상품 ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_user_product (user_id, product_id),  -- 중복 저장 방지
    KEY idx_wishlist_user_id (user_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
