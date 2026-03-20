package com.stockmanagement.domain.coupon.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.dto.CouponCreateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.CouponUsage;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import com.stockmanagement.domain.coupon.repository.CouponUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository couponUsageRepository;

    @InjectMocks private CouponService couponService;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime PAST = NOW.minusDays(1);
    private static final LocalDateTime FUTURE = NOW.plusDays(7);

    // ===== Coupon 팩토리 =====

    private Coupon fixedCoupon(BigDecimal discountValue, Integer maxUsage) {
        return Coupon.builder()
                .code("FIXED10")
                .name("고정 할인")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(discountValue)
                .maxUsageCount(maxUsage)
                .maxUsagePerUser(1)
                .validFrom(PAST)
                .validUntil(FUTURE)
                .build();
    }

    private Coupon percentCoupon(BigDecimal percent, BigDecimal maxCap) {
        return Coupon.builder()
                .code("PERCENT10")
                .name("퍼센트 할인")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(percent)
                .maxDiscountAmount(maxCap)
                .maxUsageCount(null)
                .maxUsagePerUser(1)
                .validFrom(PAST)
                .validUntil(FUTURE)
                .build();
    }

    // ===== validate =====

    @Test
    @DisplayName("validate — FIXED_AMOUNT 쿠폰 정상 할인 계산")
    void validate_fixedAmount() {
        Coupon coupon = fixedCoupon(BigDecimal.valueOf(5000), null);
        given(couponRepository.findByCode("FIXED10")).willReturn(Optional.of(coupon));
        given(couponUsageRepository.countByCoupon_IdAndUserId(any(), eq(USER_ID))).willReturn(0);

        CouponValidateResponse response = couponService.validate(USER_ID,
                new CouponValidateRequest("FIXED10", BigDecimal.valueOf(30000)));

        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000));
    }

    @Test
    @DisplayName("validate — PERCENTAGE 쿠폰 최대 캡 적용")
    void validate_percentageWithCap() {
        Coupon coupon = percentCoupon(BigDecimal.valueOf(20), BigDecimal.valueOf(3000));
        given(couponRepository.findByCode("PERCENT10")).willReturn(Optional.of(coupon));
        given(couponUsageRepository.countByCoupon_IdAndUserId(any(), eq(USER_ID))).willReturn(0);

        CouponValidateResponse response = couponService.validate(USER_ID,
                new CouponValidateRequest("PERCENT10", BigDecimal.valueOf(30000)));

        // 20% = 6000 → 캡 3000
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(27000));
    }

    @Test
    @DisplayName("validate — 만료된 쿠폰 → COUPON_EXPIRED")
    void validate_expired() {
        Coupon coupon = Coupon.builder()
                .code("OLD")
                .name("만료 쿠폰")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .maxUsagePerUser(1)
                .validFrom(NOW.minusDays(10))
                .validUntil(NOW.minusDays(1))
                .build();
        given(couponRepository.findByCode("OLD")).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate(USER_ID,
                new CouponValidateRequest("OLD", BigDecimal.valueOf(10000))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("validate — 소진 쿠폰 → COUPON_EXHAUSTED")
    void validate_exhausted() {
        Coupon coupon = fixedCoupon(BigDecimal.valueOf(1000), 5);
        for (int i = 0; i < 5; i++) coupon.increaseUsage(); // usageCount = 5

        given(couponRepository.findByCode("FIXED10")).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate(USER_ID,
                new CouponValidateRequest("FIXED10", BigDecimal.valueOf(10000))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXHAUSTED);
    }

    @Test
    @DisplayName("validate — 최소 주문 금액 미달 → COUPON_MIN_ORDER_NOT_MET")
    void validate_minOrderNotMet() {
        Coupon coupon = Coupon.builder()
                .code("MIN5000")
                .name("최소 금액 쿠폰")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .minimumOrderAmount(BigDecimal.valueOf(5000))
                .maxUsagePerUser(1)
                .validFrom(PAST)
                .validUntil(FUTURE)
                .build();
        given(couponRepository.findByCode("MIN5000")).willReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate(USER_ID,
                new CouponValidateRequest("MIN5000", BigDecimal.valueOf(3000))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_MIN_ORDER_NOT_MET);
    }

    @Test
    @DisplayName("validate — 이미 사용한 쿠폰 → COUPON_ALREADY_USED")
    void validate_alreadyUsed() {
        Coupon coupon = fixedCoupon(BigDecimal.valueOf(1000), null);
        given(couponRepository.findByCode("FIXED10")).willReturn(Optional.of(coupon));
        given(couponUsageRepository.countByCoupon_IdAndUserId(any(), eq(USER_ID))).willReturn(1);

        assertThatThrownBy(() -> couponService.validate(USER_ID,
                new CouponValidateRequest("FIXED10", BigDecimal.valueOf(10000))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_USED);
    }

    // ===== applyCoupon =====

    @Test
    @DisplayName("applyCoupon — 정상 적용: usageCount 증가 + CouponUsage 저장")
    void applyCoupon_success() {
        Coupon coupon = fixedCoupon(BigDecimal.valueOf(5000), null);
        given(couponRepository.findByCodeWithLock("FIXED10")).willReturn(Optional.of(coupon));
        given(couponUsageRepository.countByCoupon_IdAndUserId(any(), eq(USER_ID))).willReturn(0);
        given(couponUsageRepository.save(any())).willReturn(mock(CouponUsage.class));

        CouponValidateResponse result = couponService.applyCoupon(
                "FIXED10", USER_ID, ORDER_ID, BigDecimal.valueOf(30000));

        assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(coupon.getUsageCount()).isEqualTo(1);
        verify(couponUsageRepository).save(any(CouponUsage.class));
    }

    @Test
    @DisplayName("applyCoupon — 존재하지 않는 쿠폰 → COUPON_NOT_FOUND")
    void applyCoupon_notFound() {
        given(couponRepository.findByCodeWithLock("NONE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.applyCoupon(
                "NONE", USER_ID, ORDER_ID, BigDecimal.valueOf(10000)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_FOUND);
    }

    // ===== releaseCoupon =====

    @Test
    @DisplayName("releaseCoupon — 쿠폰 사용 주문 취소 시 usageCount 감소 + CouponUsage 삭제")
    void releaseCoupon_success() {
        Coupon coupon = fixedCoupon(BigDecimal.valueOf(1000), null);
        coupon.increaseUsage(); // usageCount = 1

        CouponUsage usage = CouponUsage.builder()
                .coupon(coupon)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .discountAmount(BigDecimal.valueOf(1000))
                .build();

        given(couponUsageRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(usage));

        couponService.releaseCoupon(ORDER_ID);

        assertThat(coupon.getUsageCount()).isEqualTo(0);
        verify(couponUsageRepository).delete(usage);
    }

    @Test
    @DisplayName("releaseCoupon — 쿠폰 미사용 주문 취소 시 아무 동작 없음")
    void releaseCoupon_noCoupon() {
        given(couponUsageRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        couponService.releaseCoupon(ORDER_ID);

        verify(couponUsageRepository, never()).delete(any());
    }
}
