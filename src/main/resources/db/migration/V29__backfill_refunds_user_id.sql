-- =====================================================================
-- V29: refunds.user_id 백필 + FK 제약 추가
-- =====================================================================
-- V28에서 ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 으로 컬럼을 추가했으나
-- 기존 환불 레코드의 user_id가 0으로 채워진 상태.
-- user_id=0은 존재하지 않는 사용자 ID이므로 소유권 검증이 항상 실패함.
-- 이 마이그레이션은 orders 테이블에서 실제 user_id를 조회해 백필한 뒤,
-- 참조 무결성을 보장하는 FK 제약을 추가한다.
-- =====================================================================

-- 1단계: 기존 레코드 백필 — orders.user_id 를 refunds.user_id 로 채움
UPDATE refunds r
    JOIN orders o ON r.order_id = o.id
SET r.user_id = o.user_id
WHERE r.user_id = 0;

-- 2단계: FK 제약 추가 — users 테이블 참조 무결성 보장
--   ON DELETE RESTRICT: 사용자 삭제 전 환불 이력 정리 필요 (소프트 삭제이므로 실제로는 발생 안 함)
ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_user FOREIGN KEY (user_id) REFERENCES users (id);
