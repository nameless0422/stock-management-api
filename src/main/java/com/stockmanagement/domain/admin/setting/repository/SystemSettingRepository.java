package com.stockmanagement.domain.admin.setting.repository;

import com.stockmanagement.domain.admin.setting.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
