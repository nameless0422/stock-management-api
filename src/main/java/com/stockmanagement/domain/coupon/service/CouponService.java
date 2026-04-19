package com.stockmanagement.domain.coupon.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.common.lock.DistributedLock;
import com.stockmanagement.domain.coupon.dto.CouponClaimRequest;
import com.stockmanagement.domain.coupon.dto.CouponCreateRequest;
import com.stockmanagement.domain.coupon.dto.CouponIssueRequest;
import com.stockmanagement.domain.coupon.dto.CouponResponse;
import com.stockmanagement.domain.coupon.dto.CouponValidateRequest;
import com.stockmanagement.domain.coupon.dto.CouponValidateResponse;
import com.stockmanagement.domain.coupon.dto.MyCouponResponse;
import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.CouponUsage;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import com.stockmanagement.domain.coupon.entity.UserCoupon;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import com.stockmanagement.domain.coupon.repository.CouponUsageRepository;
import com.stockmanagement.domain.coupon.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final UserCouponRepository userCouponRepository;

    // ===== ADMIN =====

    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        // 날짜 교차 검증 — 시작일이 종료일 이후면 즉시 만료 쿠폰이 생성됨
        if (request.getValidFrom().isAfter(request.getValidUntil())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "쿠폰 시작일이 종료일보다 이후일 수 없습니다.");
        }
        // PERCENTAGE 할인율 100% 초과 방지
        if (request.getDiscountType() == DiscountType.PERCENTAGE
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "할인율은 100%를 초과할 수 없습니다.");
        }
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
                .isPublic(request.isPublic())
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

    /**
     * 특정 사용자에게 쿠폰을 발급한다 (ADMIN 전용).
     * 이미 발급된 쿠폰은 중복 발급할 수 없다.
     */
    @Transactional
    public void issueToUser(Long couponId, Long userId) {
        Coupon coupon = findById(couponId);
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        try {
            userCouponRepository.save(UserCoupon.builder()
                    .userId(userId)
                    .coupon(coupon)
                    .build());
            userCouponRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
    }

    /**
     * 나에게 발급된 쿠폰 목록을 반환한다.
     * 각 쿠폰의 현재 사용 가능 여부({@code isUsable})를 함께 계산한다.
     *
     * <p>쿼리 최적화:
     * <ul>
     *   <li>@EntityGraph로 Coupon을 JOIN FETCH → UserCoupon N+1 방지
     *   <li>사용 횟수를 배치 쿼리로 한 번에 로드 → isUsable() 내 count 쿼리 N+1 방지
     * </ul>
     */
    public List<MyCouponResponse> getMyCoupons(Long userId) {
        return getMyCoupons(userId, null);
    }

    /**
     * 사용자 쿠폰 목록을 조회한다. usable 파라미터로 사용 가능 여부 필터링 지원.
     *
     * @param usable null=전체, true=사용 가능만, false=사용 불가만
     */
    public List<MyCouponResponse> getMyCoupons(Long userId, Boolean usable) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
        if (userCoupons.isEmpty()) {
            return List.of();
        }

        List<Long> couponIds = userCoupons.stream()
                .map(uc -> uc.getCoupon().getId())
                .toList();

        // 배치 조회: couponId → 이 사용자의 사용 횟수
        Map<Long, Integer> usedCountMap = couponUsageRepository
                .countByCouponIdsAndUserId(couponIds, userId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        LocalDateTime now = LocalDateTime.now();
        return userCoupons.stream()
                .map(uc -> {
                    int usedCount = usedCountMap.getOrDefault(uc.getCoupon().getId(), 0);
                    return MyCouponResponse.from(uc, isUsable(uc.getCoupon(), usedCount, now));
                })
                .filter(r -> usable == null || r.isUsable() == usable)
                .toList();
    }

    // ===== USER =====

    /**
     * 공개 쿠폰 코드를 입력해 사용자 지갑(user_coupons)에 등록한다.
     *
     * <p>isPublic = false 쿠폰은 admin이 직접 발급해야 하므로 claim 불가.
     * 이미 등록된 쿠폰(중복 claim)은 COUPON_ALREADY_ISSUED 예외를 던진다.
     *
     * @return 등록된 쿠폰 정보 + 사용 가능 여부
     */
    @Transactional
    public MyCouponResponse claim(Long userId, CouponClaimRequest request) {
        Coupon coupon = findActiveByCode(request.getCouponCode());

        if (!coupon.isPublic()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_PUBLIC);
        }
        if (userCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId())) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        // 유효기간 검증 — 만료 쿠폰은 등록 시점에 차단
        validatePeriod(coupon);

        UserCoupon userCoupon;
        try {
            userCoupon = userCouponRepository.save(
                    UserCoupon.builder().userId(userId).coupon(coupon).build());
            userCouponRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }

        int usedCount = couponUsageRepository.countByCoupon_IdAndUserId(coupon.getId(), userId);
        return MyCouponResponse.from(userCoupon, isUsable(coupon, usedCount, LocalDateTime.now()));
    }

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
     * <p>쿠폰 행 비관적 락(findByCodeWithLock)으로 전체 usageCount 동시성을 제어하고,
     * 사용자+쿠폰 분산 락(@DistributedLock)으로 동일 사용자의 동시 요청에 의한
     * maxUsagePerUser 우회(TOCTOU)를 방지한다.
     *
     * @return 쿠폰 ID와 실제 할인 금액을 담은 응답 (couponId, discountAmount)
     */
    @DistributedLock(key = "'coupon:user:' + #couponCode + ':' + #userId", leaseTime = 5)
    @Transactional
    public CouponValidateResponse applyCoupon(String couponCode, Long userId, Long orderId, BigDecimal orderAmount) {
        Coupon coupon = couponRepository.findByCodeWithLock(couponCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 비관적 락 획득 후 재검증 (TOCTOU 방지)
        validateActive(coupon);
        validateConditions(coupon, userId, orderAmount);

        // 발급 전용 쿠폰은 user_coupons 등록 여부 확인
        if (!coupon.isPublic()
                && !userCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ISSUED);
        }

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
     * 비관적 락으로 applyCoupon()과의 lost update를 방지한다.
     */
    @Transactional
    public void releaseCoupon(Long orderId) {
        Optional<CouponUsage> usageOpt = couponUsageRepository.findByOrderId(orderId);
        if (usageOpt.isEmpty()) {
            return; // 쿠폰 미사용 주문
        }
        CouponUsage usage = usageOpt.get();
        // 비관적 락으로 쿠폰 재조회 — LAZY 로드된 인스턴스 대신 잠금 보장
        Coupon coupon = couponRepository.findByIdWithLock(usage.getCoupon().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        coupon.decreaseUsage();
        couponUsageRepository.delete(usage);
    }

    // ===== 내부 검증 헬퍼 =====

    /**
     * 쿠폰이 현재 사용 가능한지 여부를 반환한다 (예외 없이 boolean 반환).
     *
     * @param usedCount 이미 로드된 이 사용자의 해당 쿠폰 사용 횟수
     * @param now       일관된 현재 시각 (getMyCoupons에서 1회 생성하여 전달)
     */
    private boolean isUsable(Coupon coupon, int usedCount, LocalDateTime now) {
        if (!coupon.isActive()) return false;
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) return false;
        if (coupon.getMaxUsageCount() != null && coupon.getUsageCount() >= coupon.getMaxUsageCount()) return false;
        return usedCount < coupon.getMaxUsagePerUser();
    }

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
            throw new BusinessException(ErrorCode.COUPON_INACTIVE);
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
