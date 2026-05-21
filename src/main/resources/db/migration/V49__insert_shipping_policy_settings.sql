-- 배송비 정책 시스템 설정 (기본: 3,000원, 50,000원 이상 무료배송)
INSERT INTO system_settings (setting_key, setting_value, description, updated_by, updated_at)
VALUES ('shipping_default_fee', '3000', '기본 배송비 (원)', 'SYSTEM', NOW());

INSERT INTO system_settings (setting_key, setting_value, description, updated_by, updated_at)
VALUES ('shipping_free_threshold', '50000', '무료배송 기준 금액 (원). 0이면 항상 유료.', 'SYSTEM', NOW());
