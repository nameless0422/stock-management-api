CREATE TABLE product_qna
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL COMMENT '대상 상품 ID',
    user_id     BIGINT       NOT NULL COMMENT '질문 작성자 ID',
    content     TEXT         NOT NULL COMMENT '질문 내용',
    secret      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '비밀글 여부',
    answer      TEXT         NULL     COMMENT '답변 내용 (ADMIN 작성)',
    answered_by BIGINT       NULL     COMMENT '답변 작성 ADMIN ID',
    answered_at DATETIME(6)  NULL     COMMENT '답변 일시',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_product_qna_product_id (product_id),
    KEY idx_product_qna_user_id (user_id),
    CONSTRAINT fk_product_qna_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_qna_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
