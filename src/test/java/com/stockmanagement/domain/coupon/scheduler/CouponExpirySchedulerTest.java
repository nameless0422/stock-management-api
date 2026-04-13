package com.stockmanagement.domain.coupon.scheduler;

import com.stockmanagement.domain.coupon.repository.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponExpiryScheduler 단위 테스트")
class CouponExpirySchedulerTest {

    @Mock CouponRepository couponRepository;
    @InjectMocks CouponExpiryScheduler scheduler;

    @Test
    @DisplayName("만료 쿠폰이 있으면 벌크 UPDATE 호출 및 건수 반환")
    void deactivatesExpiredCoupons() {
        given(couponRepository.deactivateExpiredCoupons(any())).willReturn(3);

        scheduler.deactivateExpiredCoupons();

        verify(couponRepository).deactivateExpiredCoupons(any());
    }

    @Test
    @DisplayName("만료 쿠폰이 없으면 0건 반환 (정상 종료)")
    void doesNothingWhenNoExpiredCoupons() {
        given(couponRepository.deactivateExpiredCoupons(any())).willReturn(0);

        scheduler.deactivateExpiredCoupons();

        verify(couponRepository).deactivateExpiredCoupons(any());
    }
}
