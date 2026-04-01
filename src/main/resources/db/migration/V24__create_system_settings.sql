-- =====================================================================
-- V24: system_settings 테이블 생성
-- =====================================================================
-- 운영 중 재배포 없이 변경 가능한 시스템 설정값을 저장하는 키-값 테이블.
-- 현재 사용 키:
--   low_stock_threshold : 저재고 경보 기준 (available < 이 값이면 대시보드에 표시)
-- =====================================================================

CREATE TABLE system_settings
(
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255),
    updated_by    VARCHAR(100),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 초기 기본값 삽입
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
VALUES ('low_stock_threshold', '10',
        '저재고 경보 기준값 — available < 이 값인 상품을 대시보드에 표시',
        NOW(6));
