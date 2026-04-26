-- is_default 컬럼 인덱스 추가 (user_id별 기본 배송지 빠른 조회용)
-- 기본 배송지 단일 보장은 애플리케이션 레이어(DeliveryAddressService)에서 관리
CREATE INDEX idx_delivery_addr_user_default
    ON delivery_addresses (user_id, is_default);
