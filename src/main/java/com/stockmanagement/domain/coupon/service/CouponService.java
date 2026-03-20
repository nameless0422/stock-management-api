package com.stockmanagement.domain.coupon.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.coupon.dto.CouponCreateRequest;
import com.stockmanagement.domain.coupon.dto.CouponResponse;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.CouponUsage;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import com.stockmanagement.domain.coupon.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 쿠폰 비즈니스 로직.
 *
 * <p>validate() — 읽기 전용, 할인 금액 미리보기 (사용 이력 기록 없음).
 * <p>applyCoupon() — 쓰기, 비관적 락으로 usageCount 동시성 제어.
 * <p>releaseCoupon() — 주문 취소 시 usageCount 복원.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    // ===== ADMIN =====

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        Coupon coupon = Coupon.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .maxUsageCount(request.getMaxUsageCount())
                .maxUsagePerUser(request.getMaxUsagePerUser())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .build();
        return CouponResponse.from(couponRepository.save(coupon));
    }

    public Page<CouponResponse> getList(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponResponse::from);
    }

    public CouponResponse getById(Long id) {
        return CouponResponse.from(findById(id));
    }

    @Transactional
    public CouponResponse deactivate(Long id) {
        Coupon coupon = findById(id);
        coupon.deactivate();
        return CouponResponse.from(coupon);
    }

    // ===== USER =====

    /**
     * 쿠폰 유효성 검사 + 할인 금액 미리보기.
     * 사용 이력을 기록하지 않으므로 읽기 전용.
     */
    public CouponValidateResponse validate(Long userId, CouponValidateRequest request) {
        Coupon coupon = findActiveByCode(request.getCouponCode());
        validateConditions(coupon, userId, request.getOrderAmount());
        BigDecimal discountAmount = coupon.calculateDiscount(request.getOrderAmount());
        return buildValidateResponse(coupon, request.getOrderAmount(), discountAmount);
    }

    /**
     * 쿠폰 실제 적용 — 비관적 락으로 usageCount 원자적 증가.
     * OrderService.create() 트랜잭션 내에서 호출된다.
     *
     * @return 쿠폰 ID와 실제 할인 금액을 담은 응답 (couponId, discountAmount)
     */
    @Transactional
    public CouponValidateResponse applyCoupon(String couponCode, Long userId, Long orderId, BigDecimal orderAmount) {
        Coupon coupon = couponRepository.findByCodeWithLock(couponCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 비관적 락 획득 후 재검증 (TOCTOU 방지)
        validateActive(coupon);
        validatePeriod(coupon);
        validateUsageCount(coupon);
        validateMinimumOrderAmount(coupon, orderAmount);
        validateUserUsage(coupon, userId);

        BigDecimal discountAmount = coupon.calculateDiscount(orderAmount);

        coupon.increaseUsage();
        couponUsageRepository.save(CouponUsage.builder()
                .coupon(coupon)
                .userId(userId)
                .orderId(orderId)
                .discountAmount(discountAmount)
                .build());

        return buildValidateResponse(coupon, orderAmount, discountAmount);
    }

    /**
     * 쿠폰 반환 — 주문 취소 또는 환불 시 usageCount 복원.
     * 쿠폰 미사용 주문이면 아무 동작도 하지 않는다.
     */
    @Transactional
    public void releaseCoupon(Long orderId) {
        Optional<CouponUsage> usageOpt = couponUsageRepository.findByOrderId(orderId);
        if (usageOpt.isEmpty()) {
            return; // 쿠폰 미사용 주문
        }
        CouponUsage usage = usageOpt.get();
        usage.getCoupon().decreaseUsage();
        couponUsageRepository.delete(usage);
    }

    // ===== 내부 검증 헬퍼 =====

    private Coupon findById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
    }

    private Coupon findActiveByCode(String code) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        validateActive(coupon);
        return coupon;
    }

    private void validateConditions(Coupon coupon, Long userId, BigDecimal orderAmount) {
        validatePeriod(coupon);
        validateUsageCount(coupon);
        validateMinimumOrderAmount(coupon, orderAmount);
        validateUserUsage(coupon, userId);
    }

    private void validateActive(Coupon coupon) {
        if (!coupon.isActive()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
    }

    private void validatePeriod(Coupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
    }

    private void validateUsageCount(Coupon coupon) {
        if (coupon.getMaxUsageCount() != null
                && coupon.getUsageCount() >= coupon.getMaxUsageCount()) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }
    }

    private void validateMinimumOrderAmount(Coupon coupon, BigDecimal orderAmount) {
        if (coupon.getMinimumOrderAmount() != null
                && orderAmount.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET);
        }
    }

    private void validateUserUsage(Coupon coupon, Long userId) {
        int usedCount = couponUsageRepository.countByCoupon_IdAndUserId(coupon.getId(), userId);
        if (usedCount >= coupon.getMaxUsagePerUser()) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
    }

    private CouponValidateResponse buildValidateResponse(Coupon coupon, BigDecimal orderAmount, BigDecimal discountAmount) {
        return CouponValidateResponse.builder()
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .couponName(coupon.getName())
                .orderAmount(orderAmount)
                .discountAmount(discountAmount)
                .finalAmount(orderAmount.subtract(discountAmount))
                .build();
    }
}
