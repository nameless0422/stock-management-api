-- 회원 탈퇴 처리를 위한 논리 삭제 컬럼 추가
-- deleted_at IS NULL → 활성 계정, NOT NULL → 탈퇴 계정
ALTER TABLE users ADD COLUMN deleted_at DATETIME(6) NULL;
