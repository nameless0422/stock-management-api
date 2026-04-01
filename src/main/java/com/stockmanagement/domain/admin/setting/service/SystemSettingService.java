package com.stockmanagement.domain.admin.setting.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.dto.LowStockThresholdRequest;
import com.stockmanagement.domain.admin.dto.LowStockThresholdResponse;
import com.stockmanagement.domain.admin.setting.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 설정 관리 서비스.
 *
 * <p>설정값은 "settings" 캐시(1시간 TTL)에 저장되어 매 요청마다 DB를 조회하지 않는다.
 * 값을 변경하면 즉시 캐시를 무효화하여 다음 조회 시 최신값을 반환한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SystemSettingService {

    static final String LOW_STOCK_KEY = "low_stock_threshold";
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private final SystemSettingRepository settingRepository;

    /**
     * 저재고 임계값을 반환한다.
     * DB에 값이 없으면 기본값(10)을 반환한다.
     */
    @Cacheable(value = "settings", key = "'" + LOW_STOCK_KEY + "'")
    public int getLowStockThreshold() {
        return settingRepository.findById(LOW_STOCK_KEY)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_LOW_STOCK_THRESHOLD;
                    }
                })
                .orElse(DEFAULT_LOW_STOCK_THRESHOLD);
    }

    /**
     * 저재고 임계값 상세 정보를 반환한다 (임계값 + 변경자 + 변경일시).
     */
    public LowStockThresholdResponse getLowStockThresholdDetails() {
        return settingRepository.findById(LOW_STOCK_KEY)
                .map(LowStockThresholdResponse::from)
                .orElse(LowStockThresholdResponse.ofDefault(DEFAULT_LOW_STOCK_THRESHOLD));
    }

    /**
     * 저재고 임계값을 변경한다.
     *
     * @param request   새 임계값
     * @param updatedBy 변경한 관리자 username
     */
    @Transactional
    @CacheEvict(value = "settings", key = "'" + LOW_STOCK_KEY + "'")
    public LowStockThresholdResponse updateLowStockThreshold(LowStockThresholdRequest request,
                                                             String updatedBy) {
        var setting = settingRepository.findById(LOW_STOCK_KEY)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
        setting.update(String.valueOf(request.threshold()), updatedBy);
        return LowStockThresholdResponse.from(setting);
    }
}
