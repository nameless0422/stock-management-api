package com.stockmanagement.domain.admin.setting.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.domain.admin.dto.ShippingPolicyRequest;
import com.stockmanagement.domain.admin.dto.ShippingPolicyResponse;
import com.stockmanagement.domain.admin.setting.repository.SystemSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingService 단위 테스트")
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository settingRepository;

    @InjectMocks
    private SystemSettingService systemSettingService;

    private com.stockmanagement.domain.admin.setting.entity.SystemSetting createSetting(String key, String value) {
        try {
            var clazz = com.stockmanagement.domain.admin.setting.entity.SystemSetting.class;
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            var setting = ctor.newInstance();
            var keyField = clazz.getDeclaredField("settingKey");
            keyField.setAccessible(true);
            keyField.set(setting, key);
            var valField = clazz.getDeclaredField("settingValue");
            valField.setAccessible(true);
            valField.set(setting, value);
            var byField = clazz.getDeclaredField("updatedBy");
            byField.setAccessible(true);
            byField.set(setting, "admin");
            var atField = clazz.getDeclaredField("updatedAt");
            atField.setAccessible(true);
            atField.set(setting, LocalDateTime.now());
            return setting;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("getShippingDefaultFee()")
    class GetShippingDefaultFee {

        @Test
        @DisplayName("DB 값이 있으면 해당 값을 반환한다")
        void returnsDbValue() {
            given(settingRepository.findById("shipping_default_fee"))
                    .willReturn(Optional.of(createSetting("shipping_default_fee", "5000")));

            BigDecimal fee = systemSettingService.getShippingDefaultFee();

            assertThat(fee).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("DB 값이 없으면 기본값 3000을 반환한다")
        void returnsDefault() {
            given(settingRepository.findById("shipping_default_fee"))
                    .willReturn(Optional.empty());

            BigDecimal fee = systemSettingService.getShippingDefaultFee();

            assertThat(fee).isEqualByComparingTo("3000");
        }
    }

    @Nested
    @DisplayName("calculateShippingFee()")
    class CalculateShippingFee {

        @Test
        @DisplayName("주문 금액이 무료배송 기준 이상이면 0을 반환한다")
        void freeShippingWhenAboveThreshold() {
            given(settingRepository.findById("shipping_free_threshold"))
                    .willReturn(Optional.of(createSetting("shipping_free_threshold", "50000")));

            BigDecimal fee = systemSettingService.calculateShippingFee(new BigDecimal("50000"));

            assertThat(fee).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("주문 금액이 무료배송 기준 미만이면 기본 배송비를 반환한다")
        void chargesWhenBelowThreshold() {
            given(settingRepository.findById("shipping_free_threshold"))
                    .willReturn(Optional.of(createSetting("shipping_free_threshold", "50000")));
            given(settingRepository.findById("shipping_default_fee"))
                    .willReturn(Optional.of(createSetting("shipping_default_fee", "3000")));

            BigDecimal fee = systemSettingService.calculateShippingFee(new BigDecimal("30000"));

            assertThat(fee).isEqualByComparingTo("3000");
        }
    }

    @Nested
    @DisplayName("updateShippingPolicy()")
    class UpdateShippingPolicy {

        @Test
        @DisplayName("배송비 정책을 변경한다")
        void updatesPolicy() {
            var feeSetting = createSetting("shipping_default_fee", "3000");
            var thresholdSetting = createSetting("shipping_free_threshold", "50000");
            given(settingRepository.findById("shipping_default_fee")).willReturn(Optional.of(feeSetting));
            given(settingRepository.findById("shipping_free_threshold")).willReturn(Optional.of(thresholdSetting));

            ShippingPolicyResponse response = systemSettingService.updateShippingPolicy(
                    new ShippingPolicyRequest(new BigDecimal("2500"), new BigDecimal("30000")), "admin");

            assertThat(response.defaultFee()).isEqualByComparingTo("2500");
            assertThat(response.freeShippingThreshold()).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("설정이 없으면 SETTING_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(settingRepository.findById("shipping_default_fee")).willReturn(Optional.empty());

            assertThatThrownBy(() -> systemSettingService.updateShippingPolicy(
                    new ShippingPolicyRequest(new BigDecimal("2500"), new BigDecimal("30000")), "admin"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
