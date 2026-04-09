-- 쿠폰 공개 여부 컬럼 추가
-- 0 (기본값): admin이 특정 사용자에게 발급한 쿠폰 (user_coupons 등록 필수)
-- 1: 코드를 아는 누구나 claim/사용 가능한 공개 프로모 코드
ALTER TABLE coupons
    ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '공개 쿠폰 여부 (0=발급 필요, 1=누구나 claim 가능)';
