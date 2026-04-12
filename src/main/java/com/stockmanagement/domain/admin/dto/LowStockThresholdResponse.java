package com.stockmanagement.domain.admin.dto;

import com.stockmanagement.domain.admin.setting.entity.SystemSetting;

import java.time.LocalDateTime;

/** 저재고 임계값 조회 응답 DTO — 현재 임계값, 마지막 변경자, 변경일시 포함. */
public record LowStockThresholdResponse(
        int threshold,
        String updatedBy,
        LocalDateTime updatedAt
) {
    public static LowStockThresholdResponse from(SystemSetting setting) {
        return new LowStockThresholdResponse(
                Integer.parseInt(setting.getSettingValue()),
                setting.getUpdatedBy(),
                setting.getUpdatedAt()
        );
    }

    /** DB 레코드가 없을 때 기본값으로 응답을 구성한다. */
    public static LowStockThresholdResponse ofDefault(int threshold) {
        return new LowStockThresholdResponse(threshold, null, null);
    }
}
