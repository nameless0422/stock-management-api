-- =====================================================================
-- V28: refunds 테이블에 user_id 컬럼 추가
-- =====================================================================
-- validateOwnership()에서 orderRepository.findById() 추가 SELECT를 제거하기 위해
-- userId를 refunds에 비정규화 저장한다.
-- 기존 레코드는 0으로 채우고, 운영 환경에서는 별도 데이터 마이그레이션 필요.
-- =====================================================================

ALTER TABLE refunds
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 AFTER order_id;
