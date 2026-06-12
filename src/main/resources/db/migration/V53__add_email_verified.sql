ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 사용자는 인증된 것으로 처리
UPDATE users SET email_verified = TRUE WHERE deleted_at IS NULL;
