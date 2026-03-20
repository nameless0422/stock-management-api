-- 배송지 테이블
-- 사용자별 다중 주소 관리, isDefault로 기본 배송지 1개 지정
CREATE TABLE delivery_addresses (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL,
    alias        VARCHAR(50)    NOT NULL,   -- 별칭 (집, 회사 등)
    recipient    VARCHAR(50)    NOT NULL,   -- 수령인 이름
    phone        VARCHAR(20)    NOT NULL,
    zip_code     VARCHAR(10)    NOT NULL,
    address1     VARCHAR(200)   NOT NULL,   -- 도로명/지번 주소
    address2     VARCHAR(100),              -- 상세주소 (동·호수 등)
    is_default   TINYINT(1)     NOT NULL DEFAULT 0,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,

    KEY idx_delivery_address_user (user_id),
    CONSTRAINT fk_delivery_address_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- orders 테이블에 배송지 FK 컬럼 추가 (nullable — 배송지 선택은 선택 사항)
-- ON DELETE SET NULL: 배송지 삭제 시 주문의 배송지 정보를 null로 설정 (주문 이력 보존)
ALTER TABLE orders
    ADD COLUMN delivery_address_id BIGINT NULL,
    ADD CONSTRAINT fk_orders_delivery_address
        FOREIGN KEY (delivery_address_id) REFERENCES delivery_addresses (id)
        ON DELETE SET NULL;
