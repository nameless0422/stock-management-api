package com.stockmanagement.domain.admin.setting.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.dto.LowStockThresholdRequest;
import com.stockmanagement.domain.admin.dto.LowStockThresholdResponse;
import com.stockmanagement.domain.admin.dto.ShippingPolicyRequest;
import com.stockmanagement.domain.admin.dto.ShippingPolicyResponse;
import com.stockmanagement.domain.admin.setting.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 시스템 설정 관리 서비스.
 *
 * <p>설정값은 "settings" 캐시(1시간 TTL)에 저장되어 매 요청마다 DB를 조회하지 않는다.
 * 값을 변경하면 즉시 캐시를 무효화하여 다음 조회 시 최신값을 반환한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SystemSettingService {

    static final String LOW_STOCK_KEY = "low_stock_threshold";
    static final String SHIPPING_DEFAULT_FEE_KEY = "shipping_default_fee";
    static final String SHIPPING_FREE_THRESHOLD_KEY = "shipping_free_threshold";

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("3000");
    private static final BigDecimal DEFAULT_FREE_THRESHOLD = new BigDecimal("50000");

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
                        log.warn("[SystemSetting] 저재고 임계값 파싱 실패, 기본값 사용: value={}", s.getSettingValue());
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

    // ===== Shipping Policy =====

    /** 기본 배송비를 반환한다. */
    @Cacheable(value = "settings", key = "'" + SHIPPING_DEFAULT_FEE_KEY + "'")
    public BigDecimal getShippingDefaultFee() {
        return settingRepository.findById(SHIPPING_DEFAULT_FEE_KEY)
                .map(s -> parseBigDecimal(s.getSettingValue(), DEFAULT_SHIPPING_FEE))
                .orElse(DEFAULT_SHIPPING_FEE);
    }

    /** 무료배송 기준 금액을 반환한다. 0이면 무료배송 없음 (항상 유료). */
    @Cacheable(value = "settings", key = "'" + SHIPPING_FREE_THRESHOLD_KEY + "'")
    public BigDecimal getShippingFreeThreshold() {
        return settingRepository.findById(SHIPPING_FREE_THRESHOLD_KEY)
                .map(s -> parseBigDecimal(s.getSettingValue(), DEFAULT_FREE_THRESHOLD))
                .orElse(DEFAULT_FREE_THRESHOLD);
    }

    /**
     * 주문 금액에 따른 배송비를 계산한다.
     *
     * @param orderAmount 상품 합계 금액 (쿠폰·포인트 적용 전)
     * @return 배송비 (무료배송 기준 충족 시 0)
     */
    public BigDecimal calculateShippingFee(BigDecimal orderAmount) {
        BigDecimal freeThreshold = getShippingFreeThreshold();
        if (freeThreshold.compareTo(BigDecimal.ZERO) > 0
                && orderAmount.compareTo(freeThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return getShippingDefaultFee();
    }

    /** 배송비 정책 상세 정보를 반환한다. */
    public ShippingPolicyResponse getShippingPolicyDetails() {
        var feeSetting = settingRepository.findById(SHIPPING_DEFAULT_FEE_KEY);
        var thresholdSetting = settingRepository.findById(SHIPPING_FREE_THRESHOLD_KEY);

        BigDecimal fee = feeSetting.map(s -> parseBigDecimal(s.getSettingValue(), DEFAULT_SHIPPING_FEE))
                .orElse(DEFAULT_SHIPPING_FEE);
        BigDecimal threshold = thresholdSetting.map(s -> parseBigDecimal(s.getSettingValue(), DEFAULT_FREE_THRESHOLD))
                .orElse(DEFAULT_FREE_THRESHOLD);

        // 변경 이력은 가장 최근 변경된 설정 기준
        String updatedBy = feeSetting.map(s -> s.getUpdatedBy()).orElse(null);
        var updatedAt = feeSetting.map(s -> s.getUpdatedAt()).orElse(null);

        return ShippingPolicyResponse.of(fee, threshold, updatedBy, updatedAt);
    }

    /** 배송비 정책을 변경한다. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "settings", key = "'" + SHIPPING_DEFAULT_FEE_KEY + "'"),
            @CacheEvict(value = "settings", key = "'" + SHIPPING_FREE_THRESHOLD_KEY + "'")
    })
    public ShippingPolicyResponse updateShippingPolicy(ShippingPolicyRequest request, String updatedBy) {
        var feeSetting = settingRepository.findById(SHIPPING_DEFAULT_FEE_KEY)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
        var thresholdSetting = settingRepository.findById(SHIPPING_FREE_THRESHOLD_KEY)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));

        feeSetting.update(request.defaultFee().toPlainString(), updatedBy);
        thresholdSetting.update(request.freeShippingThreshold().toPlainString(), updatedBy);

        return ShippingPolicyResponse.of(request.defaultFee(), request.freeShippingThreshold(),
                updatedBy, feeSetting.getUpdatedAt());
    }

    private BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("[SystemSetting] BigDecimal 파싱 실패, 기본값 사용: value={}", value);
            return defaultValue;
        }
    }
}
