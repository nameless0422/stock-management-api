package com.stockmanagement.domain.coupon.scheduler;

import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponExpiryScheduler 단위 테스트")
class CouponExpirySchedulerTest {

    @Mock CouponRepository couponRepository;
    @InjectMocks CouponExpiryScheduler scheduler;

    @Test
    @DisplayName("만료 쿠폰이 있으면 deactivate() 호출")
    void deactivatesExpiredCoupons() {
        Coupon expired = buildActiveCoupon("EXPIRED");
        given(couponRepository.findExpiredActiveCoupons(any())).willReturn(List.of(expired));

        scheduler.deactivateExpiredCoupons();

        // deactivate() → active=false 확인
        assert !expired.isActive();
    }

    @Test
    @DisplayName("만료 쿠폰이 없으면 deactivate() 미호출")
    void doesNothingWhenNoExpiredCoupons() {
        given(couponRepository.findExpiredActiveCoupons(any())).willReturn(List.of());

        scheduler.deactivateExpiredCoupons();

        // 빈 목록이므로 쿠폰 관련 추가 호출 없음
        verify(couponRepository, only()).findExpiredActiveCoupons(any());
    }

    // ===== 헬퍼 =====

    private Coupon buildActiveCoupon(String code) {
        Coupon coupon = Coupon.builder()
                .code(code)
                .name("테스트 쿠폰")
                .discountType(com.stockmanagement.domain.coupon.entity.DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1))
                .maxUsageCount(100)
                .build();
        ReflectionTestUtils.setField(coupon, "id", 1L);
        return coupon;
    }
}
