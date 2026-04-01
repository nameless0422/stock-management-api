package com.stockmanagement.domain.admin.setting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 운영 중 변경 가능한 시스템 설정을 저장하는 키-값 엔티티.
 *
 * <p>재배포 없이 ADMIN API를 통해 값을 변경할 수 있다.
 */
@Entity
@Table(name = "system_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(length = 255)
    private String description;

    /** 마지막으로 변경한 관리자 username */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 값 변경 — updatedBy에 변경한 관리자 username을 기록한다. */
    public void update(String value, String updatedBy) {
        this.settingValue = value;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}
